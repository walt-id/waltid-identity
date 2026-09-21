package id.walt.issuer2.models

import id.walt.issuer2.notifications.IssuanceNotifications
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.offers.TxCode
import id.walt.sdjwt.SDMap
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.minutes

// The management API and offer service currently use the same data shape.
// Keep one shared model instead of duplicating DTO and service variants until those contracts diverge.
@Serializable
data class CredentialOfferRuntimeOverrides(
    val issuerDid: String? = null,
    val issuerKey: JsonObject? = null,
    val expectedCredentialProofKeyJwk: JsonObject? = null,
    val credentialData: JsonObject? = null,
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
    val idTokenClaimsMapping: Map<String, String>? = null,
    val mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
    /** OpenID4VP transaction_data types the issued key may sign, embedded in the mdoc MSO. */
    val authorizedTransactionDataTypes: List<String>? = null,
    val x5Chain: List<String>? = null,
    val notifications: IssuanceNotifications? = null,
    val credentialStatus: JsonElement? = null,
)

@Serializable
data class CredentialOfferCreateRequest(
    val profileId: String,
    val authMethod: AuthenticationMethod,
    val issuerStateMode: IssuerStateMode? = null,
    val valueMode: CredentialOfferValueMode = CredentialOfferValueMode.BY_REFERENCE,
    val expiresInSeconds: Long = 5.minutes.inWholeSeconds,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    val runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
    val sessionId: String? = null,
) : CredentialOfferRequestBody {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(expiresInSeconds == -1L || expiresInSeconds > 0) {
            "expiresInSeconds must be positive, or -1 for no expiry"
        }
        require(txCode == null || authMethod == AuthenticationMethod.PRE_AUTHORIZED) {
            "txCode is only supported for PRE_AUTHORIZED credential offers"
        }
        require(authMethod == AuthenticationMethod.PRE_AUTHORIZED || txCodeValue == null) {
            "txCodeValue is only supported for PRE_AUTHORIZED credential offers"
        }
        require(authMethod == AuthenticationMethod.AUTHORIZED || issuerStateMode == null) {
            "issuerStateMode is only supported for AUTHORIZED credential offers"
        }
        require(
            authMethod != AuthenticationMethod.AUTHORIZED ||
                    (issuerStateMode ?: IssuerStateMode.INCLUDE) != IssuerStateMode.OMIT ||
                    runtimeOverrides == null
        ) {
            "runtimeOverrides require issuerStateMode INCLUDE for AUTHORIZED credential offers"
        }
        sessionId?.let { require(it.isNotBlank()) { "sessionId must not be blank" } }
    }
}

@Serializable
data class CredentialOfferCreateResponse(
    val offerId: String,
    val profileId: String,
    val authMethod: AuthenticationMethod,
    val issuerStateMode: IssuerStateMode? = null,
    val expiresAt: Long,
    val txCodeValue: String? = null,
    val credentialOffer: String,
)

@Serializable
data class CredentialOfferCredential(
    val profileId: String,
    val runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
    }
}

@Serializable
data class MultiCredentialOfferCreateRequest(
    val credentials: List<CredentialOfferCredential>,
    val authMethod: AuthenticationMethod,
    val issuerStateMode: IssuerStateMode? = null,
    val valueMode: CredentialOfferValueMode = CredentialOfferValueMode.BY_REFERENCE,
    val expiresInSeconds: Long = 5.minutes.inWholeSeconds,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    val sessionId: String? = null,
) : CredentialOfferRequestBody {
    init {
        require(credentials.isNotEmpty()) { "credentials must not be empty" }
        require(expiresInSeconds == -1L || expiresInSeconds > 0) {
            "expiresInSeconds must be positive, or -1 for no expiry"
        }
        require(txCode == null || authMethod == AuthenticationMethod.PRE_AUTHORIZED) {
            "txCode is only supported for PRE_AUTHORIZED credential offers"
        }
        require(authMethod == AuthenticationMethod.PRE_AUTHORIZED || txCodeValue == null) {
            "txCodeValue is only supported for PRE_AUTHORIZED credential offers"
        }
        require(authMethod == AuthenticationMethod.AUTHORIZED || issuerStateMode == null) {
            "issuerStateMode is only supported for AUTHORIZED credential offers"
        }
        require(
            authMethod != AuthenticationMethod.AUTHORIZED ||
                    (issuerStateMode ?: IssuerStateMode.INCLUDE) != IssuerStateMode.OMIT ||
                    credentials.none { it.runtimeOverrides != null }
        ) {
            "runtimeOverrides require issuerStateMode INCLUDE for AUTHORIZED credential offers"
        }
        sessionId?.let { require(it.isNotBlank()) { "sessionId must not be blank" } }
    }
}

@Serializable
data class MultiCredentialOfferCreateResponse(
    val offerId: String,
    val authMethod: AuthenticationMethod,
    val issuerStateMode: IssuerStateMode? = null,
    val expiresAt: Long,
    val txCodeValue: String? = null,
    val credentialOffer: String,
)

/** The management boundary accepts either contract without a JSON discriminator. */
@Serializable(with = CredentialOfferRequestBodySerializer::class)
sealed interface CredentialOfferRequestBody

object CredentialOfferRequestBodySerializer : JsonContentPolymorphicSerializer<CredentialOfferRequestBody>(CredentialOfferRequestBody::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<CredentialOfferRequestBody> {
        val body = element as? JsonObject ?: throw SerializationException("Expected a credential offer JSON object")
        val hasProfile = "profileId" in body
        val hasCredentials = "credentials" in body
        if (hasProfile == hasCredentials) throw SerializationException("Provide either profileId or credentials")
        if (hasProfile) {
            val profile = body["profileId"] as? JsonPrimitive
            if (profile == null || !profile.isString || profile.content.isBlank()) {
                throw SerializationException("profileId must be a non-blank string")
            }
            return CredentialOfferCreateRequest.serializer()
        }
        if ("runtimeOverrides" in body) throw SerializationException("Array offers require runtimeOverrides on individual credentials")
        val credentials = body["credentials"] as? JsonArray
        if (credentials.isNullOrEmpty()) throw SerializationException("credentials must be a non-empty array")
        return MultiCredentialOfferCreateRequest.serializer()
    }
}

internal fun CredentialOfferCreateRequest.asMultiCredentialOfferRequest() = MultiCredentialOfferCreateRequest(
    credentials = listOf(CredentialOfferCredential(profileId, runtimeOverrides)),
    authMethod = authMethod,
    issuerStateMode = issuerStateMode,
    valueMode = valueMode,
    expiresInSeconds = expiresInSeconds,
    txCode = txCode,
    txCodeValue = txCodeValue,
    sessionId = sessionId,
)
