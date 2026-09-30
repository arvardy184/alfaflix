package com.application.alfaflix.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Opens [url] in whatever app handles it (the YouTube app, or a browser).
 *
 * Silently does nothing when no app can handle the intent — a device with neither is rare,
 * and a crash would be a worse outcome than a tap that has no effect.
 */
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing can open it; nothing more to do.
    }
}
