package id.walt.openid4vp.clientidprefix.prefixes

import kotlinx.serialization.Serializable

/**
 * Represents a parsed Client Identifier that can be validated.
 * Validates the syntax of its own components upon initialization.
 */
@Serializable
sealed interface ClientId {
    val rawValue: String
}
