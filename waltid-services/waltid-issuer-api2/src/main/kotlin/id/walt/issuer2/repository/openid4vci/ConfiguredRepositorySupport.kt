package id.walt.issuer2.repository.openid4vci

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

internal fun ttlUntil(expiresAt: Instant): Duration =
    (expiresAt - Clock.System.now()).coerceAtLeast(Duration.ZERO)