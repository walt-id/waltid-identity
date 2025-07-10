package id.walt.verifier.oidc

import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectSerializer
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.providers.PresentationSession
import id.walt.oid4vc.responses.TokenResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class SwaggerTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("vp_token") val vpToken: JsonElement? = null,
    @SerialName("id_token") val idToken: String? = null,
    val scope: String? = null,
    @SerialName("c_nonce") val cNonce: String? = null,
    @SerialName("c_nonce_expires_in") val cNonceExpiresIn: Long? = null,
    @SerialName("authorization_pending") val authorizationPending: Boolean? = null,
    val interval: Long? = null,
    @SerialName("presentation_submission") val presentationSubmission: PresentationSubmission? = null,
    val state: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
    val customParameters: Map<String, JsonElement> = mapOf(),
)

@Serializable
data class SwaggerPresentationSessionInfo(
    val id: String,
    val presentationDefinition: PresentationDefinition,
    val tokenResponse: SwaggerTokenResponse? = null,
    val verificationResult: Boolean? = null,
    val policyResults: JsonObject? = null,
    val customParameters: Map<String, JsonElement> = mapOf(),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = PresentationSessionInfoSerializer::class)
data class PresentationSessionInfo(
    val id: String,
    val presentationDefinition: PresentationDefinition,
    val tokenResponse: TokenResponse? = null,
    val verificationResult: Boolean? = null,
    val policyResults: JsonObject? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf(),
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(PresentationSessionInfoSerializer, this).jsonObject

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

internal object PresentationSessionInfoSerializer :
    JsonDataObjectSerializer<PresentationSessionInfo>(PresentationSessionInfo.generatedSerializer())

