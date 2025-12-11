package id.walt.policies2.vc.status.status

import id.walt.policies2.vc.JsonObjectUtils.updateJsonArrayPlaceholders
import id.walt.policies2.vc.JsonObjectUtils.updateJsonObjectPlaceholders
import id.walt.policies2.vc.policies.status.model.StatusPolicyAttribute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray

@Serializable
data class TestStatusResource(
    val id: String,
    val data: StatusResourceData,
)

@Serializable
sealed class StatusResourceData {
    abstract val holderCredential: JsonObject//no cwt, atm
    abstract val valid: Boolean
    abstract val exception: String?

    abstract fun updateHolderCredential(vararg values: String): StatusResourceData
    abstract fun updateStatusCredential(vararg values: String): StatusResourceData

    protected fun updateHolderCredentialJson(
        holderCredential: JsonObject,
        vararg values: String
    ): JsonObject = updateJsonObjectPlaceholders(
        jsonObject = holderCredential,
        placeholder = STATUS_CREDENTIAL_PATH_PLACEHOLDER,
        valueSeparator = PLACEHOLDER_VALUE_SEPARATOR,
        placeholderValue = values
    )
}

@Serializable
@SerialName("single")
data class SingleStatusResourceData(
    @SerialName("status-credential")
    val statusCredential: String,
    @SerialName("holder-credential")
    override val holderCredential: JsonObject,
    override val valid: Boolean,
    override val exception: String? = null,
    val attribute: StatusPolicyAttribute,
) : StatusResourceData() {

    override fun updateHolderCredential(vararg values: String): SingleStatusResourceData =
        copy(holderCredential = updateHolderCredentialJson(holderCredential, *values))

    override fun updateStatusCredential(vararg values: String): StatusResourceData = this
}

@Serializable
@SerialName("multi")
data class MultiStatusResourceData(
    @SerialName("status-credential")
    val statusCredential: List<StatusCredential>,
    @SerialName("holder-credential")
    override val holderCredential: JsonObject,
    override val valid: Boolean,
    override val exception: String? = null,
    val attribute: List<StatusPolicyAttribute>,
) : StatusResourceData() {

    @Serializable
    data class StatusCredential(
        val id: String,
        val content: String,
    )

    override fun updateHolderCredential(vararg values: String): MultiStatusResourceData =
        copy(holderCredential = updateHolderCredentialJson(holderCredential, *values))

    override fun updateStatusCredential(vararg values: String): MultiStatusResourceData =
        copy(statusCredential = updateStatusCredentialList(*values))

    //not nice, but ¯\_(ツ)_/¯
    private fun updateStatusCredentialList(vararg values: String): List<StatusCredential> {
        val credentialArray = JSON_MAPPER.encodeToJsonElement(statusCredential).jsonArray
        val updatedArray = updateJsonArrayPlaceholders(
            jsonArray = credentialArray,
            placeholder = STATUS_CREDENTIAL_PATH_PLACEHOLDER,
            valueSeparator = PLACEHOLDER_VALUE_SEPARATOR,
            placeholderValue = values
        )
        return JSON_MAPPER.decodeFromJsonElement(updatedArray)
    }
}
