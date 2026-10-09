package id.walt.ktorauthnz.tokens

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Refresh tokens: a login issues a short-lived login token and a refresh token; `token/refresh` exchanges the refresh
 * token for a new pair. The session then lives [refreshTokenLifetime], each login token [accessTokenLifetime].
 * Refresh tokens are single-use: presenting one twice ends the session (it was probably stolen).
 */
data class RefreshTokenSettings(
    val accessTokenLifetime: Duration = 15.minutes,
    val refreshTokenLifetime: Duration = 30.days,
)
