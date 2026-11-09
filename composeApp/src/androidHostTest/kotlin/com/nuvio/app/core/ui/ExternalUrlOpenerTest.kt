package com.nuvio.app.core.ui

import android.net.Uri
import androidx.compose.ui.platform.UriHandler
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalUrlOpenerTest {

    @Test
    fun httpsUrlLaunchesCustomTab() {
        val systemHandlerUrls = mutableListOf<String>()
        var customTabUri: Uri? = null
        val opener = AndroidExternalUrlOpener(
            context = RuntimeEnvironment.getApplication(),
            fallback = recordingUriHandler(systemHandlerUrls),
            launchCustomTab = { _, uri -> customTabUri = uri },
        )

        assertTrue(opener.open("https://nuvio.tv/privacy-policy"))

        assertEquals("https://nuvio.tv/privacy-policy", customTabUri?.toString())
        assertTrue(systemHandlerUrls.isEmpty())
    }

    @Test
    fun httpUrlLaunchesCustomTab() {
        val systemHandlerUrls = mutableListOf<String>()
        var customTabUri: Uri? = null
        val opener = AndroidExternalUrlOpener(
            context = RuntimeEnvironment.getApplication(),
            fallback = recordingUriHandler(systemHandlerUrls),
            launchCustomTab = { _, uri -> customTabUri = uri },
        )

        assertTrue(opener.open("http://example.com/path"))

        assertEquals("http://example.com/path", customTabUri?.toString())
        assertTrue(systemHandlerUrls.isEmpty())
    }

    @Test
    fun webUrlFallsBackToSystemHandlerWhenCustomTabFails() {
        val systemHandlerUrls = mutableListOf<String>()
        val opener = AndroidExternalUrlOpener(
            context = RuntimeEnvironment.getApplication(),
            fallback = recordingUriHandler(systemHandlerUrls),
            launchCustomTab = { _, _ -> error("no Custom Tabs provider") },
        )

        assertTrue(opener.open("https://example.com"))

        assertEquals(listOf("https://example.com"), systemHandlerUrls)
    }

    @Test
    fun customSchemeUsesSystemHandlerDirectly() {
        val systemHandlerUrls = mutableListOf<String>()
        var customTabUri: Uri? = null
        val opener = AndroidExternalUrlOpener(
            context = RuntimeEnvironment.getApplication(),
            fallback = recordingUriHandler(systemHandlerUrls),
            launchCustomTab = { _, uri -> customTabUri = uri },
        )

        assertTrue(opener.open("nuvio://auth/callback?code=123"))

        assertNull(customTabUri)
        assertEquals(listOf("nuvio://auth/callback?code=123"), systemHandlerUrls)
    }

    @Test
    fun blankUrlReturnsFalseWithoutOpeningAnything() {
        val systemHandlerUrls = mutableListOf<String>()
        var customTabUri: Uri? = null
        val opener = AndroidExternalUrlOpener(
            context = RuntimeEnvironment.getApplication(),
            fallback = recordingUriHandler(systemHandlerUrls),
            launchCustomTab = { _, uri -> customTabUri = uri },
        )

        assertFalse(opener.open("   "))

        assertNull(customTabUri)
        assertTrue(systemHandlerUrls.isEmpty())
    }

    @Test
    fun trimsWhitespaceBeforeOpening() {
        val systemHandlerUrls = mutableListOf<String>()
        var customTabUri: Uri? = null
        val opener = AndroidExternalUrlOpener(
            context = RuntimeEnvironment.getApplication(),
            fallback = recordingUriHandler(systemHandlerUrls),
            launchCustomTab = { _, uri -> customTabUri = uri },
        )

        assertTrue(opener.open("  https://nuvio.tv/terms  "))

        assertEquals("https://nuvio.tv/terms", customTabUri?.toString())
    }

    @Test
    fun returnsFalseWhenEveryHandlerFails() {
        val opener = AndroidExternalUrlOpener(
            context = RuntimeEnvironment.getApplication(),
            fallback = object : UriHandler {
                override fun openUri(uri: String) = error("no system handler")
            },
            launchCustomTab = { _, _ -> error("no Custom Tabs provider") },
        )

        assertFalse(opener.open("https://example.com"))
    }

    private fun recordingUriHandler(openedUrls: MutableList<String>): UriHandler =
        object : UriHandler {
            override fun openUri(uri: String) {
                openedUrls += uri
            }
        }
}
