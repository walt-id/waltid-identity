package id.walt.openid4vci

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

internal val DEFAULT_AUTHORIZATION_CODE_LIFETIME_SECONDS = 5.minutes.inWholeSeconds
internal val DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS = 30.minutes.inWholeSeconds
internal val DEFAULT_REFRESH_TOKEN_LIFETIME_SECONDS = 1.days.inWholeSeconds
