@file:OptIn(ExperimentalJsExport::class)

package id.walt.policies

import id.walt.credentials.verification.policies.JsonSchemaPolicy
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration

@JsExport
@Serializable
sealed class SerializableRuntimeException(override val message: String? = null) : RuntimeException(message)

@JsExport
@Serializable
@SerialName("JsonSchemaVerificationException")
data class JsonSchemaVerificationException(val validationErrors: List<JsonSchemaPolicy.SerializableValidationError>) :
    id.walt.policies.SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("NotBeforePolicyException")
data class NotBeforePolicyException(
    val date: Instant,
    @SerialName("date_seconds")
    val dateSeconds: Long,

    @SerialName("available_in")
    val availableIn: Duration,
    @SerialName("available_in_seconds")
    val availableInSeconds: Long,
    val key: String,
    @SerialName("policy_available")
    val policyAvailable: Boolean = true,
) :
    id.walt.policies.SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("ExpirationDatePolicyException")
data class ExpirationDatePolicyException(
    val date: Instant,
    @SerialName("date_seconds")
    val dateSeconds: Long,

    @SerialName("expired_in")
    val expiredSince: Duration,
    @SerialName("expired_in_seconds")
    val expiredSinceSeconds: Long,
    val key: String,
    @SerialName("policy_available")
    val policyAvailable: Boolean = true,
) :
    id.walt.policies.SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("WebhookPolicyException")
data class WebhookPolicyException(
    val response: JsonObject,
) :
    id.walt.policies.SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("PresentationDefinitionException")
class PresentationDefinitionException(
    val missingCredentialTypes: List<String>,
) : id.walt.policies.SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("MinimumCredentialsException")
class MinimumCredentialsException(
    val total: Int,
    val missing: Int,
) : id.walt.policies.SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("MaximumCredentialsException")
class MaximumCredentialsException(
    val total: Int,
    val exceeded: Int,
) : id.walt.policies.SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("HolderBindingException")
class HolderBindingException(
    val presenterDid: String,
    val credentialDids: List<String>,
) : id.walt.policies.SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("NotAllowedIssuerException")
class NotAllowedIssuerException(
    val issuer: String,
    val allowedIssuers: List<String>,
) : id.walt.policies.SerializableRuntimeException()
