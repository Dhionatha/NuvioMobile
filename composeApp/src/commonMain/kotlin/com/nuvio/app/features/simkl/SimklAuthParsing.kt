package com.nuvio.app.features.simkl

import io.ktor.http.Url
import io.ktor.http.encodeURLParameter

internal const val SIMKL_AUTHORIZATION_TIMEOUT_MS = 5L * 60L * 1_000L

/**
 * AUTH V2 scopes requested by this app. Writes imply reads, but both are sent so the request
 * matches the scope Simkl reports back on the token.
 */
internal const val SIMKL_OAUTH2_SCOPE = "media:read media:write"
internal const val SIMKL_OAUTH2_WRITE_SCOPE = "media:write"

internal fun parseSimklAuthCallback(
    callbackUrl: String,
    redirectUri: String,
): SimklAuthCallback {
    if (callbackUrl != redirectUri && !callbackUrl.startsWith("$redirectUri?")) {
        return SimklAuthCallback.NotSimkl
    }
    val parsed = runCatching { Url(callbackUrl) }.getOrNull() ?: return SimklAuthCallback.Invalid
    val code = parsed.parameters["code"].orEmpty().trim()
    val state = parsed.parameters["state"].orEmpty().trim()
    if (code.isBlank() || state.isBlank()) return SimklAuthCallback.Invalid
    return SimklAuthCallback.AuthorizationCode(code = code, state = state)
}

internal fun isSimklAuthorizationExpired(
    startedAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean = startedAtEpochMs == null ||
    nowEpochMs < startedAtEpochMs ||
    nowEpochMs - startedAtEpochMs > SIMKL_AUTHORIZATION_TIMEOUT_MS

internal fun constantTimeEquals(left: String, right: String): Boolean {
    val leftBytes = left.encodeToByteArray()
    val rightBytes = right.encodeToByteArray()
    val length = maxOf(leftBytes.size, rightBytes.size)
    var difference = leftBytes.size xor rightBytes.size
    for (index in 0 until length) {
        val leftByte = leftBytes.getOrElse(index) { 0 }.toInt()
        val rightByte = rightBytes.getOrElse(index) { 0 }.toInt()
        difference = difference or (leftByte xor rightByte)
    }
    return difference == 0
}

internal fun buildSimklAuthorizationUrl(
    clientId: String,
    redirectUri: String,
    appName: String,
    appVersion: String,
    material: SimklPkceMaterial,
): String = buildString {
    append(SIMKL_AUTHORIZE_URL)
    append("?response_type=code")
    append("&client_id=")
    append(clientId.encodeURLParameter())
    append("&redirect_uri=")
    append(redirectUri.encodeURLParameter())
    append("&code_challenge=")
    append(material.challenge.encodeURLParameter())
    append("&code_challenge_method=S256")
    if (SimklConfig.USE_OAUTH2) {
        // AUTH V2 silently grants a read-only token when `scope` is omitted, which makes every
        // write (lists, history, scrobbles) fail later with 403 insufficient_scope.
        append("&scope=")
        append(SIMKL_OAUTH2_SCOPE.encodeURLParameter())
    }
    append("&state=")
    append(material.state.encodeURLParameter())
    append("&app-name=")
    append(appName.encodeURLParameter())
    append("&app-version=")
    append(appVersion.encodeURLParameter())
}

/**
 * AUTH V2 silently downgrades to `media:read` when `scope` is missing or misspelled, and a
 * read-only grant fails every Simkl write with `403 insufficient_scope`. AUTH V1 tokens have no
 * scopes at all, so only V2 grants are checked.
 */
internal fun simklGrantedScopeAllowsWrites(grantedScope: String?): Boolean {
    if (!SimklConfig.USE_OAUTH2) return true
    val scope = grantedScope?.trim().orEmpty()
    if (scope.isEmpty()) return false
    return scope.split(' ', '\t', '\n', '\r').any { it == SIMKL_OAUTH2_WRITE_SCOPE }
}
