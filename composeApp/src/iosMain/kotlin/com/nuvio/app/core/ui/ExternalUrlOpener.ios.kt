package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIViewController

@Composable
actual fun rememberExternalUrlOpener(): ExternalUrlOpener {
    val fallback = LocalUriHandler.current
    val host = LocalUIViewController.current
    return remember(host, fallback) {
        IosExternalUrlOpener(host = host, fallback = fallback)
    }
}

/**
 * Presents web pages with `SFSafariViewController`, which keeps Nuvio in the back stack and shares
 * the user's browser session. Non-web schemes and hosts that cannot present fall back to the
 * platform URI handler.
 */
internal class IosExternalUrlOpener(
    private val host: UIViewController,
    private val fallback: UriHandler,
    private val present: (UIViewController, UIViewController) -> Unit = { from, controller ->
        from.presentViewController(controller, animated = true, completion = null)
    },
) : ExternalUrlOpener {
    override fun open(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isEmpty()) return false
        val target = NSURL.URLWithString(normalized) ?: return openWithPlatformHandler(normalized)
        val scheme = target.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return openWithPlatformHandler(normalized)

        val presenter = host.topPresentedViewController()
        // Re-tapping the same rating while the sheet is animating must not stack browsers.
        if (presenter is SFSafariViewController) return true

        val safari = SFSafariViewController(uRL = target)
        return runCatching { present(presenter, safari) }.isSuccess || openWithPlatformHandler(normalized)
    }

    private fun openWithPlatformHandler(url: String): Boolean =
        runCatching { fallback.openUri(url) }.isSuccess

    private fun UIViewController.topPresentedViewController(): UIViewController {
        var current = this
        while (true) {
            current = current.presentedViewController ?: return current
        }
    }
}
