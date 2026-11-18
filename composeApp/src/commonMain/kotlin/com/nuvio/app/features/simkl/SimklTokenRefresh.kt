package com.nuvio.app.features.simkl

import io.ktor.http.encodeURLParameter

internal const val SIMKL_USER_TOKEN_REQUIRED_ERROR = "user_token_required"
internal const val SIMKL_INVALID_GRANT_ERROR = "invalid_grant"
internal const val SIMKL_INVALID_SCOPE_ERROR = "invalid_scope"

/**
 * What a refresh response means for the stored grant. AUTH V2 invalidates the previous access
 * token on every refresh, but only `invalid_grant` / `invalid_scope` prove the grant itself is
 * gone. Anything else (transport failures, 5xx) may still leave the old refresh token usable.
 */
internal enum class SimklRefreshResponseAction {
    ACCEPT,
    INVALIDATE,
    PRESERVE,
}

internal fun simklRefreshResponseAction(
    status: Int,
    errorCode: String?,
): SimklRefreshResponseAction = when {
    status in 200..299 -> SimklRefreshResponseAction.ACCEPT
    status == 401 -> SimklRefreshResponseAction.INVALIDATE
    status == 400 && errorCode in setOf(SIMKL_INVALID_GRANT_ERROR, SIMKL_INVALID_SCOPE_ERROR) ->
        SimklRefreshResponseAction.INVALIDATE
    else -> SimklRefreshResponseAction.PRESERVE
}

/**
 * AUTH V2 echoes the same (non-rotating) refresh token on every refresh. A missing value is a
 * server quirk, not evidence that the previous refresh token stopped working.
 */
internal fun simklRefreshTokenValue(
    responseToken: String?,
    previousToken: String?,
): String? = responseToken?.takeIf(String::isNotBlank) ?: previousToken

/** Form-encoded body used by `/oauth2/token` (refresh grant) and `/oauth2/revoke`. */
internal fun simklFormEncodedBody(fields: List<Pair<String, String>>): String =
    fields.joinToString("&") { (key, value) ->
        "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
    }

/** Returns `null` when the response does not disclose a usable lifetime. */
internal fun simklAccessTokenExpiresAt(
    expiresInSeconds: Long?,
    nowEpochMs: Long,
): Long? = expiresInSeconds
    ?.takeIf { seconds -> seconds > 0L && seconds <= (Long.MAX_VALUE - nowEpochMs) / 1_000L }
    ?.let { seconds -> nowEpochMs + seconds * 1_000L }
