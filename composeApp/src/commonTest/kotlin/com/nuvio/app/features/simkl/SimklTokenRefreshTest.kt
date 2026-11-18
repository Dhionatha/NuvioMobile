package com.nuvio.app.features.simkl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimklTokenRefreshTest {
    @Test
    fun `refresh response classification clears only definitive grant failures`() {
        assertEquals(SimklRefreshResponseAction.ACCEPT, simklRefreshResponseAction(200, null))
        assertEquals(SimklRefreshResponseAction.ACCEPT, simklRefreshResponseAction(204, null))
        assertEquals(
            SimklRefreshResponseAction.INVALIDATE,
            simklRefreshResponseAction(400, SIMKL_INVALID_GRANT_ERROR),
        )
        assertEquals(
            SimklRefreshResponseAction.INVALIDATE,
            simklRefreshResponseAction(400, SIMKL_INVALID_SCOPE_ERROR),
        )
        assertEquals(SimklRefreshResponseAction.INVALIDATE, simklRefreshResponseAction(401, null))
        assertEquals(
            SimklRefreshResponseAction.PRESERVE,
            simklRefreshResponseAction(400, "invalid_request"),
        )
        assertEquals(SimklRefreshResponseAction.PRESERVE, simklRefreshResponseAction(500, null))
        assertEquals(SimklRefreshResponseAction.PRESERVE, simklRefreshResponseAction(503, null))
    }

    @Test
    fun `refresh token keeps the previous value when the response omits it`() {
        assertEquals("previous", simklRefreshTokenValue(null, "previous"))
        assertEquals("previous", simklRefreshTokenValue("  ", "previous"))
        assertEquals("echoed", simklRefreshTokenValue("echoed", "previous"))
        assertNull(simklRefreshTokenValue(null, null))
    }

    @Test
    fun `form body is url encoded and joined with ampersands`() {
        assertEquals(
            "grant_type=refresh_token&token=abc",
            simklFormEncodedBody(
                listOf(
                    "grant_type" to "refresh_token",
                    "token" to "abc",
                ),
            ),
        )
        assertEquals("token=x%26y", simklFormEncodedBody(listOf("token" to "x&y")))
    }

    @Test
    fun `expiry ignores unusable lifetimes and guards overflow`() {
        assertNull(simklAccessTokenExpiresAt(null, 1_000L))
        assertNull(simklAccessTokenExpiresAt(0L, 1_000L))
        assertNull(simklAccessTokenExpiresAt(-1L, 1_000L))
        assertNull(simklAccessTokenExpiresAt(Long.MAX_VALUE, 1_000L))
        assertEquals(61_000L, simklAccessTokenExpiresAt(60L, 1_000L))
    }
}
