package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable

/**
 * Opens external URLs in an in-app browser surface when the platform supports it.
 *
 * Android uses Chrome Custom Tabs and iOS uses `SFSafariViewController`, so both platforms keep the
 * app in the back stack. Falls back to the platform URI handler when the in-app surface is
 * unavailable.
 * Returns `true` when a handler accepted the URL and `false` when every attempt failed.
 */
fun interface ExternalUrlOpener {
    fun open(url: String): Boolean
}

@Composable
expect fun rememberExternalUrlOpener(): ExternalUrlOpener
