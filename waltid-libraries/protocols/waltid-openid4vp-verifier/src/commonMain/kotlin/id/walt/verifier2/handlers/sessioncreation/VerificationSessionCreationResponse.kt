package id.walt.verifier2.handlers.sessioncreation

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class VerificationSessionCreationResponse(
    val sessionId: String,
    val bootstrapAuthorizationRequestUrl: Url?,
    val fullAuthorizationRequestUrl: Url,
    val creationTarget: String? = null,

    // Custom data:
    val data: JsonElement? = null,
)
