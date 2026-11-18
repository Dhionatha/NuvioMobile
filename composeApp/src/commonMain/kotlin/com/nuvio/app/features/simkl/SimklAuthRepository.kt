package com.nuvio.app.features.simkl

import co.touchlab.kermit.Logger
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SimklAuthRepository : TrackingAuthProvider {
    private val log = Logger.withTag("SimklAuth")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val authorizationMutex = Mutex()
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(SimklAuthUiState())
    val uiState: StateFlow<SimklAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.SIMKL,
        displayName = "Simkl",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
            TrackingCapability.WATCHED_READ,
            TrackingCapability.WATCHED_WRITE,
            TrackingCapability.PROGRESS_READ,
            TrackingCapability.PROGRESS_WRITE,
            TrackingCapability.SCROBBLE,
            TrackingCapability.RATINGS,
        ),
    )

    private var hasLoaded = false
    private var storedState = SimklStoredAuthState()
    private var accessToken: String? = null
    private var refreshToken: String? = null

    internal var storage: SimklAuthStorageApi = SimklAuthStorage
    internal var nowEpochMs: () -> Long = { SimklPlatformClock.nowEpochMs() }
    internal var authHttpEngine: SimklHttpEngine = simklHttpEngine

    init {
        TrackingProviderRegistry.register(this)
    }

    override fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    override fun onProfileChanged() {
        loadFromDisk()
    }

    override fun clearLocalState() {
        hasLoaded = false
        storedState = SimklStoredAuthState()
        accessToken = null
        refreshToken = null
        storage.saveAccessToken(null)
        storage.saveRefreshToken(null)
        publish(isLoading = false, error = null)
    }

    override fun removeStoredProfile(profileId: Int) {
        storage.removeProfile(profileId)
    }

    fun snapshot(): SimklAuthUiState {
        ensureLoaded()
        return uiState.value
    }

    fun hasRequiredCredentials(): Boolean = SimklConfig.CLIENT_ID.isNotBlank()

    fun onConnectRequested(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) {
            publish(error = SimklAuthError.MISSING_CLIENT_ID)
            return null
        }

        val material = generateSimklPkceMaterial()
        storage.saveCodeVerifier(material.verifier)
        storedState = storedState.copy(
            pendingAuthorizationState = material.state,
            pendingAuthorizationStartedAtEpochMs = nowEpochMs(),
        )
        persistMetadata()
        publish(error = null)
        return authorizationUrl(material)
    }

    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val state = storedState.pendingAuthorizationState?.takeIf(String::isNotBlank) ?: return null
        val verifier = storage.loadCodeVerifier()?.takeIf(String::isNotBlank) ?: run {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        if (isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = nowEpochMs(),
            )
        ) {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return authorizationUrl(
            SimklPkceMaterial(
                verifier = verifier,
                challenge = SimklPkceCrypto.sha256(verifier.encodeToByteArray()).base64UrlWithoutPadding(),
                state = state,
            ),
        )
    }

    fun onCancelAuthorization() {
        ensureLoaded()
        clearPendingAuthorization()
        persistMetadata()
        publish(error = null)
    }

    override fun handleAuthCallback(url: String): Boolean {
        ensureLoaded()
        return when (val callback = parseSimklAuthCallback(url, SimklConfig.REDIRECT_URI)) {
            SimklAuthCallback.NotSimkl -> false
            SimklAuthCallback.Invalid -> {
                clearPendingAuthorization()
                persistMetadata()
                publish(error = SimklAuthError.INVALID_CALLBACK)
                true
            }
            is SimklAuthCallback.AuthorizationCode -> {
                scope.launch { completeAuthorization(callback) }
                true
            }
        }
    }

    fun onDisconnectRequested() {
        ensureLoaded()
        val revokedRefreshToken = refreshToken?.takeIf(String::isNotBlank)
        accessToken = null
        refreshToken = null
        storage.saveAccessToken(null)
        storage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(error = null)
        // Local state is already gone; revoking the grant is best effort and must not block the
        // UI or be able to resurrect credentials if the request fails.
        if (SimklConfig.USE_OAUTH2 && revokedRefreshToken != null) {
            scope.launch { revokeAuthorization(revokedRefreshToken) }
        }
    }

    internal suspend fun authorizedAccessToken(): String? {
        ensureLoaded()
        return refreshAccessToken()
    }

    /**
     * Returns a usable access token, refreshing the AUTH V2 grant when the stored token is close
     * to expiring or when the server rejected [rejectedAccessToken].
     *
     * Concurrent callers share [refreshMutex]; whoever loses the race sees the new token and
     * returns it without minting a second one. `null` means no token is available right now.
     * Definitive grant failures clear local credentials; transport failures preserve them.
     */
    internal suspend fun refreshAccessToken(rejectedAccessToken: String? = null): String? {
        ensureLoaded()
        return refreshMutex.withLock {
            val profileId = ProfileRepository.activeProfileId
            val currentToken = accessToken?.takeIf(String::isNotBlank)

            if (
                rejectedAccessToken != null &&
                currentToken != null &&
                currentToken != rejectedAccessToken
            ) {
                return@withLock currentToken
            }

            val expiresAt = storedState.tokenExpiresAtEpochMs
            val sessionExpired = expiresAt != null && nowEpochMs() >= expiresAt - TOKEN_EXPIRY_SKEW_MS
            val hasRefreshGrant = !refreshToken.isNullOrBlank()
            if (
                rejectedAccessToken == null &&
                !sessionExpired &&
                (currentToken != null || !hasRefreshGrant)
            ) {
                return@withLock currentToken
            }

            if (!SimklConfig.USE_OAUTH2) {
                if (rejectedAccessToken != null) {
                    invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
                }
                return@withLock if (sessionExpired) null else currentToken
            }

            val storedRefreshToken = refreshToken?.takeIf(String::isNotBlank)
            if (storedRefreshToken == null) {
                // Users upgrading from a build that never stored a refresh token must not be
                // signed out just because the access token is expiring. A rejected token is
                // definitive, though: without a grant only a new authorization can recover.
                if (rejectedAccessToken != null) {
                    invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
                }
                return@withLock null
            }

            val body = simklFormEncodedBody(
                listOf(
                    "grant_type" to "refresh_token",
                    "client_id" to SimklConfig.CLIENT_ID,
                    "refresh_token" to storedRefreshToken,
                ),
            )
            val response = runCatching {
                authHttpEngine.execute(
                    method = "POST",
                    url = buildSimklApiUrl(simklTokenEndpointPath),
                    headers = simklRequestHeaders() + ("Content-Type" to FORM_CONTENT_TYPE),
                    body = body,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                log.w { "Simkl token refresh transport failure: ${error.message}" }
            }.getOrNull() ?: return@withLock null

            // The profile or the grant may have changed while the request was in flight.
            if (ProfileRepository.activeProfileId != profileId || refreshToken != storedRefreshToken) {
                return@withLock null
            }

            when (simklRefreshResponseAction(response.status, response.errorCode(json))) {
                SimklRefreshResponseAction.INVALIDATE -> {
                    log.w {
                        "Simkl rejected the refresh token with HTTP ${response.status}; " +
                            "clearing local credentials"
                    }
                    invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
                    return@withLock null
                }

                SimklRefreshResponseAction.PRESERVE -> {
                    log.w { "Simkl token refresh failed with HTTP ${response.status}" }
                    return@withLock null
                }

                SimklRefreshResponseAction.ACCEPT -> Unit
            }

            val token = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }
                .getOrNull()
                ?.takeIf { it.accessToken.isNotBlank() }
            if (token == null) {
                log.w { "Simkl token refresh returned an invalid body" }
                return@withLock null
            }

            val nextRefreshToken = simklRefreshTokenValue(token.refreshToken, storedRefreshToken)
            accessToken = token.accessToken
            refreshToken = nextRefreshToken
            storage.saveAccessToken(token.accessToken)
            storage.saveRefreshToken(nextRefreshToken)
            storedState = storedState.copy(
                tokenExpiresAtEpochMs = simklAccessTokenExpiresAt(token.expiresIn, nowEpochMs()),
            )
            persistMetadata()
            publish(error = null)
            token.accessToken
        }
    }

    /** Best-effort revocation run after the local credentials were already cleared. */
    internal suspend fun revokeAuthorization(refreshToken: String): Boolean {
        if (refreshToken.isBlank() || !SimklConfig.USE_OAUTH2) return false
        return try {
            val response = authHttpEngine.execute(
                method = "POST",
                url = buildSimklApiUrl(REVOKE_ENDPOINT_PATH),
                headers = simklRequestHeaders() + ("Content-Type" to FORM_CONTENT_TYPE),
                body = simklFormEncodedBody(
                    listOf(
                        "client_id" to SimklConfig.CLIENT_ID,
                        "token" to refreshToken,
                    ),
                ),
            )
            response.status in 200..299
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Simkl token revocation failed: ${error.message}" }
            false
        }
    }

    internal fun onUnauthorizedResponse() {
        invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
    }

    /**
     * AUTH V2 cannot widen a grant by refreshing, so a token minted without `media:write` has to
     * be replaced by a new authorization round-trip.
     */
    internal fun onInsufficientScopeResponse() {
        invalidateCredentials(SimklAuthError.INSUFFICIENT_SCOPE)
    }

    suspend fun refreshUserSettings(): String? {
        authorizedAccessToken() ?: return null
        return if (fetchAndStoreUserSettings()) storedState.username else null
    }

    internal suspend fun synchronizeUserSettings(activityWatermark: String?) {
        authorizedAccessToken() ?: return
        when (simklSettingsRefreshAction(storedState, activityWatermark)) {
            SimklSettingsRefreshAction.NONE -> Unit
            SimklSettingsRefreshAction.RECORD_WATERMARK -> {
                storedState = storedState.copy(settingsActivityWatermark = activityWatermark)
                persistMetadata()
            }
            SimklSettingsRefreshAction.FETCH -> {
                fetchAndStoreUserSettings(activityWatermark)
            }
        }
    }

    private suspend fun completeAuthorization(callback: SimklAuthCallback.AuthorizationCode) =
        authorizationMutex.withLock {
            publish(isLoading = true, error = null)
            val expectedState = storedState.pendingAuthorizationState
            val verifier = storage.loadCodeVerifier()
            val isExpired = isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = nowEpochMs(),
            )
            if (expectedState.isNullOrBlank() || verifier.isNullOrBlank() || isExpired) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.AUTHORIZATION_EXPIRED)
                return@withLock
            }
            if (!constantTimeEquals(callback.state, expectedState)) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_CALLBACK_STATE)
                return@withLock
            }

            val request = SimklTokenRequest(
                code = callback.code,
                clientId = SimklConfig.CLIENT_ID,
                codeVerifier = verifier,
                redirectUri = SimklConfig.REDIRECT_URI,
            )
            val response = try {
                SimklApi.client.execute(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = simklTokenEndpointPath,
                        body = json.encodeToString(request),
                        requiresAuthentication = false,
                        retryPolicy = SimklRetryPolicy.NEVER,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w { "Simkl token exchange failed: ${error.message}" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
                return@withLock
            }
            val token = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }
                .getOrNull()
                ?.takeIf { it.accessToken.isNotBlank() }
            if (token == null) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_TOKEN_RESPONSE)
                return@withLock
            }
            if (!simklGrantedScopeAllowsWrites(token.scope)) {
                log.w {
                    "Simkl granted a read-only token (scope=${token.scope}); " +
                        "writes require $SIMKL_OAUTH2_SCOPE"
                }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INSUFFICIENT_SCOPE)
                return@withLock
            }

            accessToken = token.accessToken
            storage.saveAccessToken(token.accessToken)
            // AUTH V2 echoes the same non-rotating refresh token, but a reconnect may still omit
            // it; keep the previous value instead of dropping the grant.
            refreshToken = simklRefreshTokenValue(token.refreshToken, refreshToken)
            storage.saveRefreshToken(refreshToken)
            clearPendingAuthorization()
            storedState = storedState.copy(
                tokenExpiresAtEpochMs = simklAccessTokenExpiresAt(token.expiresIn, nowEpochMs()),
            )
            persistMetadata()
            publish(isLoading = false, error = null)
            fetchAndStoreUserSettings()
            SimklSyncRepository.refreshAsync(
                intent = TrackingRefreshIntent.INVALIDATED,
                origin = SimklRefreshOrigin.AUTHORIZATION,
            )
        }

    private suspend fun fetchAndStoreUserSettings(activityWatermark: String? = null): Boolean {
        val response = try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = "/users/settings",
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Failed to fetch Simkl user settings: ${error.message}" }
            return false
        }
        val settings = runCatching { json.decodeFromString<SimklUserSettingsResponse>(response.body) }
            .getOrNull() ?: return false
        storedState = storedState.copy(
            username = settings.user?.name,
            accountId = settings.account?.id,
            hasFetchedUserSettings = true,
            settingsActivityWatermark = activityWatermark ?: storedState.settingsActivityWatermark,
        )
        persistMetadata()
        publish(error = null)
        return true
    }

    private fun loadFromDisk() {
        hasLoaded = true
        storedState = storage.loadMetadataPayload()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { payload ->
                runCatching { json.decodeFromString<SimklStoredAuthState>(payload) }
                    .onFailure { error -> log.w { "Failed to parse Simkl auth metadata: ${error.message}" } }
                    .getOrNull()
            }
            ?: SimklStoredAuthState()
        accessToken = storage.loadAccessToken()?.takeIf(String::isNotBlank)
        refreshToken = storage.loadRefreshToken()?.takeIf(String::isNotBlank)
        if (storedState.tokenExpiresAtEpochMs?.let { expiresAt ->
                nowEpochMs() >= expiresAt - TOKEN_EXPIRY_SKEW_MS
            } == true
        ) {
            // Soft expiry: keep the refresh token and metadata (also on disk) so the next request
            // can refresh silently instead of signing the user out.
            accessToken = null
        }
        if (storedState.hasPendingAuthorization && isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = nowEpochMs(),
            )
        ) {
            clearPendingAuthorization()
            persistMetadata()
        }
        publish(error = null)
    }

    private fun invalidateCredentials(error: SimklAuthError) {
        accessToken = null
        refreshToken = null
        storage.saveAccessToken(null)
        storage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(isLoading = false, error = error)
    }

    private fun clearPendingAuthorization() {
        storage.saveCodeVerifier(null)
        storedState = storedState.copy(
            pendingAuthorizationState = null,
            pendingAuthorizationStartedAtEpochMs = null,
        )
    }

    private fun persistMetadata() {
        storage.saveMetadataPayload(json.encodeToString(storedState))
    }

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: SimklAuthError? = _uiState.value.error,
    ) {
        val authenticated = !accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()
        _isAuthenticated.value = authenticated
        _uiState.value = SimklAuthUiState(
            mode = when {
                authenticated -> SimklConnectionMode.CONNECTED
                storedState.hasPendingAuthorization -> SimklConnectionMode.AWAITING_APPROVAL
                else -> SimklConnectionMode.DISCONNECTED
            },
            credentialsConfigured = hasRequiredCredentials(),
            isLoading = isLoading,
            username = storedState.username,
            accountId = storedState.accountId,
            tokenExpiresAtEpochMs = storedState.tokenExpiresAtEpochMs,
            pendingAuthorizationStartedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
            error = error,
        )
    }

    private fun authorizationUrl(material: SimklPkceMaterial): String =
        buildSimklAuthorizationUrl(
            clientId = SimklConfig.CLIENT_ID,
            redirectUri = SimklConfig.REDIRECT_URI,
            appName = SimklConfig.APP_NAME,
            appVersion = simklAppVersion,
            material = material,
        )

    private const val TOKEN_EXPIRY_SKEW_MS = 60_000L
    private const val REVOKE_ENDPOINT_PATH = "/oauth2/revoke"
    private const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"
}

@Serializable
private data class SimklTokenRequest(
    val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("code_verifier") val codeVerifier: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("grant_type") val grantType: String = "authorization_code",
)

@Serializable
private data class SimklTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
private data class SimklUserSettingsResponse(
    val user: SimklUser? = null,
    val account: SimklAccount? = null,
)

@Serializable
private data class SimklUser(
    val name: String? = null,
)

@Serializable
private data class SimklAccount(
    val id: Long? = null,
)
