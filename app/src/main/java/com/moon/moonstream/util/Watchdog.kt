package com.moon.moonstream.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.concurrent.thread

/**
 * Freeze + crash trap (moon's 2026-11-16 bug: the app randomly froze and
 * died twice — once on the Link-to-Title tap, once after a backward seek —
 * and left NO log behind, so there was nothing to diagnose from).
 *
 * Two parts, both writing straight to the log FILE via
 * [AppLog.appendSync] — the normal add() path posts to the main thread
 * first, so during a freeze it queues behind the blockage and never
 * flushes, and during a crash the process dies before the async writer
 * runs. The sync write is the only path that survives both.
 *
 * 1. Uncaught-exception handler: any thread that dies uncaught writes
 *    its stack trace to the file, then the previous handler runs as
 *    normal (the app still dies — we only add the evidence).
 * 2. ANR watchdog: a background thread pings the main thread every
 *    second. If main hasn't answered for 5s, the watchdog dumps main's
 *    current stack trace to the file (once per stall) — the exact line
 *    the UI was stuck on. When main recovers, the flag resets.
 *
 * Started once from AppLog.init(). Costs one sleeping thread + one
 * no-op posted to main per second; zero cost while the app is healthy.
 */
object Watchdog {
    private const val PING_INTERVAL_MS = 1000L
    private const val STUCK_THRESHOLD_MS = 5000L
    private const val MAX_TRACE_LINES = 40

    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true

        // 1. Crash breadcrumbs.
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val trace = e.stackTraceToString()
                    .lineSequence().take(MAX_TRACE_LINES).joinToString("\n")
                AppLog.appendSync(
                    "E", "CRASH",
                    "Uncaught on ${t.name}: $e\n$trace",
                )
            } catch (_: Exception) { /* never break the crash path */ }
            prevHandler?.uncaughtException(t, e)
        }

        // 2. ANR watchdog.
        thread(name = "watchdog", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            val mainHandler = Handler(Looper.getMainLooper())
            @Volatile var lastHandled = SystemClock.uptimeMillis()
            var reported = false
            while (true) {
                try {
                    Thread.sleep(PING_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@thread
                }
                mainHandler.post { lastHandled = SystemClock.uptimeMillis() }
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    return@thread
                }
                val lag = SystemClock.uptimeMillis() - lastHandled
                if (lag >= STUCK_THRESHOLD_MS) {
                    if (!reported) {
                        reported = true
                        try {
                            val main = Looper.getMainLooper().thread
                            val trace = main.stackTrace
                                .take(MAX_TRACE_LINES)
                                .joinToString("\n") { "    at $it" }
                            AppLog.appendSync(
                                "E", "WATCHDOG",
                                "MAIN THREAD STUCK ${lag}ms — stack:\n$trace",
                            )
                        } catch (_: Exception) { /* never break the watchdog */ }
                    }
                } else {
                    reported = false
                }
            }
        }
    }
}
