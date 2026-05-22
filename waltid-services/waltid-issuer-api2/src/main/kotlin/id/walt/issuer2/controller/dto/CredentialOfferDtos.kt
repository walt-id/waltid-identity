package id.walt.issuer2.controller.dto

import id.walt.issuer2.domain.AuthenticationMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.minutes

@Serializable
enum class CredentialOfferDeliveryMode {
    BY_REFERENCE,
    BY_VALUE,
}

@Serializable
data class CredentialOfferRuntimeOverrides(
    val subjectId: String? = null,
    val issuerDid: String? = null,
    val issuerKeyId: String? = null,
    val credentialData: JsonObject? = null,
    val mapping: JsonObject? = null,
    val idTokenClaimsMapping: Map<String, String>? = null,
    val mdocNamespacesDataMapping: Map<String, JsonObject>? = null,
    val webhookUrl: String? = null,
)

@Serializable
data class CreateCredentialOfferRequest(
    val profileId: String,
    val authenticationMethod: AuthenticationMethod,
    val deliveryMode: CredentialOfferDeliveryMode = CredentialOfferDeliveryMode.BY_REFERENCE,
    val expiresInSeconds: Long = 5.minutes.inWholeSeconds,
    val includeIssuerState: Boolean = true,
    val runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
    val sessionId: String? = null,
)

@Serializable
data class CreateCredentialOfferResponse(
    val sessionId: String,
    val profileId: String,
    val profileVersion: Int,
    val authenticationMethod: AuthenticationMethod,
    val expiresAt: Long,
    val credentialOfferUri: String,
)
