package id.walt.openid4vp.clientidprefix.prefixes

import kotlinx.serialization.Serializable

/**
 * Handles an unsupported prefix.
 */
@Serializable
data class Unsupported(val prefix: String, override val rawValue: String) : ClientId
