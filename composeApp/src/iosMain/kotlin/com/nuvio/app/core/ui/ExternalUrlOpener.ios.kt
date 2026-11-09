package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

@Composable
actual fun rememberExternalUrlOpener(): ExternalUrlOpener {
    val fallback = LocalUriHandler.current
    return remember(fallback) {
        ExternalUrlOpener { url ->
            runCatching { fallback.openUri(url) }.isSuccess
        }
    }
}
