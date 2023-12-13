package id.walt.verifier.oidc

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.providers.PresentationSession
import id.walt.oid4vc.responses.TokenResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class PresentationSessionInfo(
    val id: String,
    val presentationDefinition: PresentationDefinition,
    val tokenResponse: TokenResponse? = null,
    val verificationResult: Boolean? = null,
    val policyResults: JsonObject? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("presentation_definition", presentationDefinition.toJSON())
        tokenResponse?.let { put("token_response", it.toJSON()) }
        verificationResult?.let { put("verification_result", JsonPrimitive(it)) }
    }

    companion object {
        fun fromPresentationSession(session: PresentationSession, policyResults: JsonObject? = null) =
            PresentationSessionInfo(
                id = session.id,
                presentationDefinition = session.presentationDefinition,
                tokenResponse = session.tokenResponse,
                verificationResult = session.verificationResult,
                policyResults = policyResults
            )
    }
}
