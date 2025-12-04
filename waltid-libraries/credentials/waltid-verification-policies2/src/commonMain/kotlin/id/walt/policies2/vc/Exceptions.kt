@file:OptIn(ExperimentalJsExport::class, ExperimentalTime::class)

package id.walt.policies2.vc

import id.walt.policies2.vc.policies.JsonSchemaPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@JsExport
@Serializable
sealed class SerializableRuntimeException(
    override val message: String? = null
) : RuntimeException(message)

@JsExport
@Serializable
@SerialName("JsonSchemaVerificationException")
data class JsonSchemaVerificationException(
    val validationErrors: List<JsonSchemaPolicy.SerializableValidationError>
) : SerializableRuntimeException()

@Serializable
@SerialName("NotBeforePolicyException")
data class NotBeforePolicyException(
    val date: Instant,
    @SerialName("date_seconds") val dateSeconds: Long,

    @SerialName("available_in") val availableIn: Duration,
    @SerialName("available_in_seconds") val availableInSeconds: Long,
    val key: String,
    @SerialName("policy_available") val policyAvailable: Boolean = true,
) : SerializableRuntimeException()

@Serializable
@SerialName("ExpirationDatePolicyException")
data class ExpirationDatePolicyException(
    val date: Instant,
    @SerialName("date_seconds") val dateSeconds: Long,

    @SerialName("expired_in") val expiredSince: Duration,
    @SerialName("expired_in_seconds") val expiredSinceSeconds: Long,
    val key: String,
    @SerialName("policy_available") val policyAvailable: Boolean = true,
) : SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("WebhookPolicyException")
data class WebhookPolicyException(
    val response: JsonElement,
) : SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("PresentationDefinitionException")
class PresentationDefinitionException(
//    val missingCredentialTypes: List<String>,
    val presentationDefinitionMatch: Boolean
) : SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("PresentationDefinitionRelationalConstraintException")
class PresentationDefinitionRelationalConstraintException(
    val constraint: RelationalConstraintType,
    val constraintFailureDescription: String
) : SerializableRuntimeException() {

    @Suppress("EnumEntryName")
    enum class RelationalConstraintType {
        subject_is_issuer,
        is_holder,
        same_subject
    }

}

@JsExport
@Serializable
@SerialName("MinimumCredentialsException")
class MinimumCredentialsException(
    val total: Int,
    val missing: Int,
) : SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("MaximumCredentialsException")
class MaximumCredentialsException(
    val total: Int,
    val exceeded: Int,
) : SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("HolderBindingException")
class HolderBindingException(
    val presenterDid: String,
    val credentialDids: List<String>,
) : SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("NotAllowedIssuerException")
class NotAllowedIssuerException(
    val issuer: String?,
    val allowedIssuers: List<String>,
) : SerializableRuntimeException()

@JsExport
@Serializable
@SerialName("DcqlClaimValidationFailure")
class DcqlClaimValidationFailureException(
    val dcqlClaimFailureInformation: String,
    val claimPath: List<String>,
    val resolvedValue: JsonElement?,
) : SerializableRuntimeException()


class DynamicPolicyException(
    override val message: String
) : Exception(message)
