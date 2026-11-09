package com.moon.moonstream.index.tools

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.index.audit.MetadataAudit
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs Library Tools (audits, dead-link check, duplicate finder) in an
 * application-scoped background coroutine.
 *
 * moon's fix 2026-11-02: these used to run in the Settings screen's
 * coroutine scope, so leaving the screen cancelled them mid-run. Now
 * they survive screen changes; the UI observes [runningTool] and
 * [progressText] to show live status.
 */
object LibraryToolsRunner {
    const val TOOL_AUDIT = "audit"
    const val TOOL_DEEP_AUDIT = "deep_audit"
    const val TOOL_DEAD_LINKS = "dead_links"
    const val TOOL_DUPLICATES = "duplicates"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Which tool is running, or null if idle. Observed by the UI. */
    var runningTool by mutableStateOf<String?>(null)
        private set

    /** Progress like "Auditing 12/150", or null. Observed by the UI. */
    var progressText by mutableStateOf<String?>(null)
        private set

    val isRunning: Boolean get() = runningTool != null

    fun isToolRunning(tool: String): Boolean = runningTool == tool

    private fun guard(): Boolean {
        if (isRunning) {
            MsToast.show("A library tool is already running")
            return false
        }
        return true
    }

    fun startAudit(appContext: Context, structuralOnly: Boolean) {
        if (!guard()) return
        val tool = if (structuralOnly) TOOL_AUDIT else TOOL_DEEP_AUDIT
        runningTool = tool
        progressText = "Starting…"
        scope.launch {
            try {
                val report = MetadataAudit.run(
                    appContext, structuralOnly,
                    onProgress = { done, total ->
                        progressText = "Auditing $done/$total"
                    },
                )
                val msg = if (report.moved == 0) "No bad mappings found"
                else "${report.moved} file(s) moved to unmatched — see logs"
                MsToast.show(msg)
                AppLog.i("LIB", "Audit done: checked=${report.checked} moved=${report.moved}")
            } catch (e: Exception) {
                MsToast.show("Audit failed: ${e.message}")
                AppLog.e("LIB", "Audit failed: ${e.message}")
            } finally {
                runningTool = null
                progressText = null
            }
        }
    }

    fun startDeadLinks(appContext: Context) {
        if (!guard()) return
        runningTool = TOOL_DEAD_LINKS
        progressText = "Starting…"
        scope.launch {
            try {
                val db = MsDatabase.get(appContext)
                val backend = BackendProvider.backend
                val files = db.streamDao().getAll()
                var dead = 0
                var unchecked = 0
                var floodWait = false
                // Entries that lost a file — their counts are refreshed below.
                val touched = mutableSetOf<Long>()
                for ((idx, f) in files.withIndex()) {
                    progressText = "Checking ${idx + 1}/${files.size} (dead: $dead)"
                    when (backend.messageExists(f.channelId, f.messageId)) {
                        // The message is gone: the row can never play, so move
                        // it to Unmatched (where the user can see and delete
                        // it) instead of leaving a broken entry in the library.
                        false -> {
                            dead++
                            db.unmatchedDao().insert(
                                UnmatchedFileEntity(
                                    channelId = f.channelId,
                                    messageId = f.messageId,
                                    fileName = f.fileName,
                                    fileSize = f.fileSize,
                                ),
                            )
                            db.streamDao().deleteById(f.id)
                            touched.add(f.mediaId)
                            AppLog.w(
                                "LIB",
                                "Dead link → unmatched: ${f.fileName} (${f.channelId}/${f.messageId})",
                            )
                        }
                        // Unknown (network / flood wait): never guess. Stop
                        // after a flood wait so a big library doesn't turn
                        // into a flood of failed requests.
                        null -> {
                            unchecked++
                            if (unchecked >= 5) {
                                floodWait = true
                                break
                            }
                        }
                        true -> { /* alive */ }
                    }
                    delay(250)
                }
                // Fix the counts of entries a dead file was taken out of, and
                // drop entries that are now empty (but keep any that still
                // have subtitles pointing at them).
                val now = System.currentTimeMillis()
                for (mediaId in touched) {
                    db.mediaDao().refreshAggregates(mediaId, now)
                    db.mediaDao().deleteIfEmpty(mediaId)
                }
                val msg = when {
                    floodWait -> "Stopped early: Telegram is rate-limiting. $dead dead so far, $unchecked unchecked."
                    dead == 0 && unchecked == 0 -> "No dead links found"
                    dead == 0 -> "No dead links found ($unchecked couldn't be checked)"
                    else -> "$dead dead link(s) moved to Unmatched" +
                        if (unchecked > 0) ", $unchecked couldn't be checked" else ""
                }
                MsToast.show(msg)
                AppLog.i(
                    "LIB",
                    "Dead-link check done: $dead dead, $unchecked unchecked of ${files.size}",
                )
            } catch (e: Exception) {
                MsToast.show("Dead-link check failed: ${e.message}")
                AppLog.e("LIB", "Dead-link check failed: ${e.message}")
            } finally {
                runningTool = null
                progressText = null
            }
        }
    }

    fun startDuplicates(appContext: Context) {
        if (!guard()) return
        runningTool = TOOL_DUPLICATES
        progressText = "Scanning…"
        scope.launch {
            try {
                val db = MsDatabase.get(appContext)
                val files = db.streamDao().getAll()

                // Two different things are worth reporting (moon's 2026-11-09):
                //  1. the SAME file in two messages — name + exact byte size;
                //  2. two files fighting for the SAME slot on one entry —
                //     same media + season + episode + quality, which is what
                //     "Duplicate protection" normally prevents.
                val sameFile = files.groupBy {
                    it.fileName.lowercase().replace(Regex("[^a-z0-9]"), "") to it.fileSize
                }.filter { it.value.size > 1 }

                val sameSlot = files
                    .filter { it.season != null || it.episode != null }
                    .groupBy { Triple(it.mediaId, "${it.season ?: "-"}:${it.episode ?: "-"}", it.quality.lowercase()) }
                    .filter { it.value.size > 1 }

                for ((_, dupes) in sameFile) {
                    AppLog.w(
                        "LIB",
                        "Duplicate file: '${dupes.first().fileName}' ×${dupes.size} " +
                            "(${dupes.size} messages, same name and size)",
                    )
                }
                for ((_, dupes) in sameSlot) {
                    val d = dupes.first()
                    AppLog.w(
                        "LIB",
                        "Duplicate slot: S${d.season ?: "?"}E${d.episode ?: "?"} " +
                            "${d.quality} ×${dupes.size} — ${dupes.joinToString { it.fileName.take(30) }}",
                    )
                }

                val count = sameFile.values.sumOf { it.size - 1 }
                val slots = sameSlot.values.sumOf { it.size - 1 }
                val msg = when {
                    count == 0 && slots == 0 -> "No duplicates found"
                    slots == 0 -> "$count duplicate file(s) found — see logs"
                    count == 0 -> "$slots episode slot(s) with more than one file — see logs"
                    else -> "$count duplicate file(s) and $slots double-filled episode slot(s) — see logs"
                }
                MsToast.show(msg)
                AppLog.i("LIB", "Duplicate scan done: $count file dupes, $slots slot dupes")
            } catch (e: Exception) {
                MsToast.show("Duplicate scan failed: ${e.message}")
                AppLog.e("LIB", "Duplicate scan failed: ${e.message}")
            } finally {
                runningTool = null
                progressText = null
            }
        }
    }
}
