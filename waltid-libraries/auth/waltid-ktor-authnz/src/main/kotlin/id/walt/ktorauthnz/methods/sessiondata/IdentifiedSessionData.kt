package id.walt.ktorauthnz.methods.sessiondata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the user identified with in the `identify` step: the identifier type (`email`, `username`) and value. */
@Serializable
@SerialName("identified")
data class IdentifiedSessionData(
    val identifierType: String,
    val value: String,
) : SessionData
