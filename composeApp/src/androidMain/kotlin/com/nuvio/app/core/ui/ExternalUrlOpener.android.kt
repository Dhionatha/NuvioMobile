package com.nuvio.app.core.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler

@Composable
actual fun rememberExternalUrlOpener(): ExternalUrlOpener {
    val context = LocalContext.current
    val fallback = LocalUriHandler.current
    return remember(context, fallback) {
        AndroidExternalUrlOpener(
            context = context,
            fallback = fallback,
        )
    }
}

internal class AndroidExternalUrlOpener(
    private val context: Context,
    private val fallback: UriHandler,
    private val launchCustomTab: (Context, Uri) -> Unit = { targetContext, uri ->
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(targetContext, uri)
    },
) : ExternalUrlOpener {
    override fun open(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isEmpty()) return false
        val uri = Uri.parse(normalized)

        if (!uri.isWebUrl()) {
            return openWithSystemHandler(normalized)
        }

        return runCatching { launchCustomTab(context, uri) }.isSuccess ||
            openWithSystemHandler(normalized)
    }

    private fun openWithSystemHandler(url: String): Boolean =
        runCatching { fallback.openUri(url) }.isSuccess
}

private fun Uri.isWebUrl(): Boolean =
    scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
