package com.nuvio.app.features.simkl

import com.nuvio.app.features.addons.RawHttpResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimklAuthRepositoryTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private var nowEpochMs = NOW_EPOCH_MS
    private lateinit var storage: FakeSimklAuthStorage

    @AfterTest
    fun tearDown() {
        SimklAuthRepository.clearLocalState()
        SimklAuthRepository.storage = SimklAuthStorage
        SimklAuthRepository.nowEpochMs = { SimklPlatformClock.nowEpochMs() }
        SimklAuthRepository.authHttpEngine = simklHttpEngine
    }

    @Test
    fun `rejected token refreshes and retries the request through the repository`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine { request ->
            when {
                request.url.contains(simklTokenEndpointPath) -> response(200, tokenBody("new-token"))
                request.headers["Authorization"] == "Bearer old-token" -> {
                    response(401, """{"error":"invalid_token","code":401}""")
                }
                else -> response(200, "{}")
            }
        }
        configure(engine)
        val client = repositoryBackedClient(engine)

        client.execute(SimklApiRequest(SimklHttpMethod.GET, "/sync/activities"))

        assertEquals(3, engine.requests.size)
        assertEquals("Bearer new-token", engine.requests.last().headers["Authorization"])
        assertEquals("new-token", storage.accessToken)
        assertEquals("refresh-token", storage.refreshToken)
        assertTrue(SimklAuthRepository.isAuthenticated.value)
    }

    @Test
    fun `concurrent refreshes mint a single token request`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val gate = CompletableDeferred<Unit>()
        val engine = ScriptedSimklEngine {
            gate.await()
            response(200, tokenBody("new-token"))
        }
        configure(engine)

        val first = async { SimklAuthRepository.refreshAccessToken("old-token") }
        withTimeout(TEST_TIMEOUT_MS) {
            while (engine.requests.isEmpty()) yield()
        }
        val second = async { SimklAuthRepository.refreshAccessToken("old-token") }
        yield()
        gate.complete(Unit)

        assertEquals("new-token", first.await())
        assertEquals("new-token", second.await())
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun `invalid grant clears credentials and publishes a revoked state`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine {
            response(400, """{"error":"invalid_grant","code":400}""")
        }
        configure(engine)

        assertNull(SimklAuthRepository.refreshAccessToken("old-token"))

        assertNull(storage.accessToken)
        assertNull(storage.refreshToken)
        assertFalse(SimklAuthRepository.isAuthenticated.value)
        assertEquals(
            SimklAuthError.AUTHORIZATION_REVOKED,
            SimklAuthRepository.uiState.value.error,
        )
    }

    @Test
    fun `transport failure during refresh preserves the grant`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine { throw IllegalStateException("offline") }
        configure(engine)

        assertNull(SimklAuthRepository.refreshAccessToken("old-token"))

        assertEquals("old-token", storage.accessToken)
        assertEquals("refresh-token", storage.refreshToken)
        assertTrue(SimklAuthRepository.isAuthenticated.value)
        assertNull(SimklAuthRepository.uiState.value.error)
    }

    @Test
    fun `expired token on disk keeps the refresh grant and refreshes on demand`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine {
            response(200, tokenBody("new-token"))
        }
        configure(engine, expiresAtEpochMs = NOW_EPOCH_MS - 1L)

        // Soft expiry never rewrites the stored access token or the metadata.
        assertEquals("old-token", storage.accessToken)
        assertEquals("refresh-token", storage.refreshToken)
        assertTrue(SimklAuthRepository.isAuthenticated.value)
        assertNull(SimklAuthRepository.uiState.value.error)

        assertEquals("new-token", SimklAuthRepository.authorizedAccessToken())
        assertEquals(1, engine.requests.size)
        assertEquals("new-token", storage.accessToken)
        assertEquals(NOW_EPOCH_MS + DAY_MS, SimklAuthRepository.uiState.value.tokenExpiresAtEpochMs)
    }

    @Test
    fun `refresh response without a refresh token keeps the previous one`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine {
            response(200, tokenBody("new-token", refreshToken = null))
        }
        configure(engine)

        assertEquals("new-token", SimklAuthRepository.refreshAccessToken("old-token"))

        assertEquals("refresh-token", storage.refreshToken)
        assertTrue(engine.requests.single().body.contains("refresh_token=refresh-token"))
    }

    @Test
    fun `missing access token with a refresh grant refreshes proactively`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine {
            response(200, tokenBody("new-token"))
        }
        configure(engine, accessToken = null)

        assertEquals("new-token", SimklAuthRepository.authorizedAccessToken())

        assertEquals(1, engine.requests.size)
        assertTrue(engine.requests.single().body.contains("refresh_token=refresh-token"))
    }

    @Test
    fun `rejected token without a refresh grant ends the session`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine { response(200) }
        configure(engine, refreshToken = null)

        assertNull(SimklAuthRepository.refreshAccessToken("old-token"))

        assertTrue(engine.requests.isEmpty())
        assertNull(storage.accessToken)
        assertNull(storage.refreshToken)
        assertEquals(
            SimklAuthError.AUTHORIZATION_REVOKED,
            SimklAuthRepository.uiState.value.error,
        )
    }

    @Test
    fun `proactive expiry without a refresh grant does not force a logout`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine { response(200) }
        configure(engine, refreshToken = null, expiresAtEpochMs = NOW_EPOCH_MS - 1L)

        assertNull(SimklAuthRepository.authorizedAccessToken())

        assertTrue(engine.requests.isEmpty())
        assertEquals("old-token", storage.accessToken)
        assertNull(SimklAuthRepository.uiState.value.error)
    }

    @Test
    fun `revoke posts the refresh token as a form without auth`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine { response(200) }
        configure(engine)

        assertTrue(SimklAuthRepository.revokeAuthorization("refresh-token"))

        val request = engine.requests.single()
        assertEquals("POST", request.method)
        assertTrue(request.url.startsWith("https://api.simkl.com/oauth2/revoke"))
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        assertFalse("Authorization" in request.headers)
        val fields = request.body.split('&').associate { field ->
            field.substringBefore('=') to field.substringAfter('=')
        }
        assertEquals("refresh-token", fields["token"])
        assertEquals(SimklConfig.CLIENT_ID, fields["client_id"])
    }

    @Test
    fun `revoke failure is swallowed once the local state is gone`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val engine = ScriptedSimklEngine { throw IllegalStateException("offline") }
        configure(engine)

        assertFalse(SimklAuthRepository.revokeAuthorization("refresh-token"))
        assertFalse(SimklAuthRepository.revokeAuthorization(""))
    }

    @Test
    fun `disconnect clears local state before the best effort revoke`() = runBlocking {
        if (!SimklConfig.USE_OAUTH2) return@runBlocking
        val gate = CompletableDeferred<Unit>()
        val engine = ScriptedSimklEngine {
            gate.await()
            response(200)
        }
        configure(engine)

        SimklAuthRepository.onDisconnectRequested()

        assertNull(storage.accessToken)
        assertNull(storage.refreshToken)
        assertFalse(SimklAuthRepository.isAuthenticated.value)
        assertEquals(SimklConnectionMode.DISCONNECTED, SimklAuthRepository.uiState.value.mode)

        withTimeout(TEST_TIMEOUT_MS) {
            while (engine.requests.isEmpty()) yield()
        }
        assertEquals("POST", engine.requests.single().method)
        gate.complete(Unit)
    }

    private fun configure(
        engine: ScriptedSimklEngine,
        accessToken: String? = "old-token",
        refreshToken: String? = "refresh-token",
        expiresAtEpochMs: Long? = NOW_EPOCH_MS + DAY_MS,
    ) {
        storage = FakeSimklAuthStorage().apply {
            this.accessToken = accessToken
            this.refreshToken = refreshToken
            metadata = json.encodeToString(
                SimklStoredAuthState(tokenExpiresAtEpochMs = expiresAtEpochMs),
            )
        }
        SimklAuthRepository.storage = storage
        SimklAuthRepository.nowEpochMs = { nowEpochMs }
        SimklAuthRepository.authHttpEngine = engine
        SimklAuthRepository.onProfileChanged()
    }

    private fun repositoryBackedClient(engine: SimklHttpEngine): SimklApiClient = SimklApiClient(
        engine = engine,
        accessToken = { SimklAuthRepository.authorizedAccessToken() },
        onTokenRejected = { rejectedToken ->
            SimklAuthRepository.refreshAccessToken(rejectedToken)
        },
        onUnauthorized = { SimklAuthRepository.onUnauthorizedResponse() },
        onInsufficientScope = { SimklAuthRepository.onInsufficientScopeResponse() },
        nowEpochMs = { nowEpochMs },
        sleep = { delayMs -> nowEpochMs += delayMs },
        retryJitterMs = { 0L },
    )

    private fun tokenBody(
        accessToken: String,
        refreshToken: String? = "refresh-token",
        expiresIn: Long? = DAY_SECONDS,
    ): String = buildString {
        append("""{"access_token":"""").append(accessToken).append('"')
        if (refreshToken != null) {
            append(""","refresh_token":"""").append(refreshToken).append('"')
        }
        if (expiresIn != null) {
            append(""","expires_in":""").append(expiresIn)
        }
        append('}')
    }

    private class FakeSimklAuthStorage : SimklAuthStorageApi {
        var metadata: String? = null
        var accessToken: String? = null
        var refreshToken: String? = null
        var codeVerifier: String? = null
        val removedProfiles = mutableListOf<Int>()

        override fun loadMetadataPayload(): String? = metadata
        override fun saveMetadataPayload(payload: String) {
            metadata = payload
        }

        override fun loadAccessToken(): String? = accessToken
        override fun saveAccessToken(value: String?) {
            accessToken = value
        }

        override fun loadRefreshToken(): String? = refreshToken
        override fun saveRefreshToken(value: String?) {
            refreshToken = value
        }

        override fun loadCodeVerifier(): String? = codeVerifier
        override fun saveCodeVerifier(value: String?) {
            codeVerifier = value
        }

        override fun removeProfile(profileId: Int) {
            removedProfiles += profileId
        }
    }

    private class ScriptedSimklEngine(
        private val handler: suspend (RecordedRequest) -> RawHttpResponse,
    ) : SimklHttpEngine {
        val requests = mutableListOf<RecordedRequest>()

        override suspend fun execute(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String,
        ): RawHttpResponse {
            val request = RecordedRequest(method, url, headers, body)
            requests += request
            return handler(request)
        }
    }

    private data class RecordedRequest(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private companion object {
        const val NOW_EPOCH_MS = 1_000_000L
        const val DAY_SECONDS = 86_400L
        const val DAY_MS = DAY_SECONDS * 1_000L
        const val TEST_TIMEOUT_MS = 5_000L

        fun response(
            status: Int,
            body: String = "{}",
        ) = RawHttpResponse(
            status = status,
            statusText = "",
            url = "https://api.simkl.com/test",
            body = body,
            headers = emptyMap(),
        )
    }
}
