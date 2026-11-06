package com.moon.moonstream.ui.model

/** Human-readable byte sizes for the real library ("14.7 GB", "812 MB"). */
fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    bytes < 1024L * 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes < 1024L * 1024 * 1024 * 1024 * 1024 -> String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024))
    else -> String.format("%.1f PB", bytes / (1024.0 * 1024 * 1024 * 1024 * 1024))
}

/** "just now", "4m ago", "2h ago", "3d ago" — for "watched X ago" labels. */
fun timeAgo(ts: Long): String {
    val mins = (System.currentTimeMillis() - ts) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}
