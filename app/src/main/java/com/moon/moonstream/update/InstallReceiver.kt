package com.moon.moonstream.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Receives PackageInstaller session results for self-updates.
 * (Part of TMPlayer's Play Protect popup fix — v1.28.3.)
 */
class InstallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "InstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Shouldn't happen with USER_ACTION_NOT_REQUIRED, but handle it.
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start confirmation", e)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Self-update installed successfully")
            }
            else -> {
                Log.e(TAG, "Install failed: status=$status message=$message")
            }
        }
    }
}
