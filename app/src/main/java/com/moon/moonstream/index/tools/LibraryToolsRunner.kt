package com.moon.moonstream.index.tools

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.index.audit.MetadataAudit
import com.moon.moonstream.index.db.MsDatabase
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
                for ((idx, f) in files.withIndex()) {
                    progressText = "Checking ${idx + 1}/${files.size}"
                    try {
                        // FIX 2026-11-05: fetchMessages is EXCLUSIVE of fromMessageId,
                        // so to fetch message N you must pass N+1 (was passing N,
                        // which fetched N-1 and always missed → all 500 marked dead).
                        val msg = backend.fetchMessages(f.channelId, f.messageId + 1, 1)
                            .firstOrNull { it.id == f.messageId }
                        if (msg == null) {
                            dead++
                            AppLog.w(
                                "LIB",
                                "Dead link: ${f.fileName} (${f.channelId}/${f.messageId})",
                            )
                        }
                    } catch (e: Exception) {
                        // Network error — skip, don't count as dead.
                    }
                    delay(100)
                }
                val msg = if (dead == 0) "No dead links found"
                else "$dead dead link(s) found — see logs"
                MsToast.show(msg)
                AppLog.i("LIB", "Dead-link check done: $dead dead of ${files.size}")
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
                // Group by normalized name + size; >1 in a group = duplicate.
                val groups = files.groupBy {
                    it.fileName.lowercase().replace(Regex("[^a-z0-9]"), "") to it.fileSize
                }.filter { it.value.size > 1 }
                val count = groups.values.sumOf { it.size - 1 }
                for ((_, dupes) in groups) {
                    AppLog.w("LIB", "Duplicate: ${dupes.first().fileName} ×${dupes.size}")
                }
                val msg = if (count == 0) "No duplicates found"
                else "$count duplicate file(s) found — see logs"
                MsToast.show(msg)
                AppLog.i("LIB", "Duplicate scan done: $count dupes")
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
