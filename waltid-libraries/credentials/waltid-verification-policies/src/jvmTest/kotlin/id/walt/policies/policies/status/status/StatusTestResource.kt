package id.walt.policies.policies.status.status

import id.walt.policies.JsonObjectUtils.updateJsonArrayPlaceholders
import id.walt.policies.JsonObjectUtils.updateJsonObjectPlaceholders
import id.walt.policies.policies.status.model.StatusPolicyAttribute
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class TestStatusResource(
    val id: String,
    val data: StatusResourceData,
)

@Serializable
sealed class StatusResourceData {
    abstract val holderCredential: JsonObject
    abstract val valid: Boolean
    abstract fun updateHolderCredential(vararg values: String): StatusResourceData
    abstract fun updateStatusCredential(vararg values: String): StatusResourceData
}

@Serializable
@SerialName("single")
data class SingleStatusResourceData(
    @SerialName("status-credential")
    val statusCredential: String,
    @SerialName("holder-credential")
    override val holderCredential: JsonObject,
    override val valid: Boolean,
    val attribute: StatusPolicyAttribute,
) : StatusResourceData() {

    override fun updateHolderCredential(vararg values: String): SingleStatusResourceData = copy(
        holderCredential = updateJsonObjectPlaceholders(
            holderCredential,
            STATUS_CREDENTIAL_PATH_PLACEHOLDER,
            PLACEHOLDER_VALUE_SEPARATOR,
            *values
        )
    )

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
    val attribute: List<StatusPolicyAttribute>,
) : StatusResourceData() {

    @Transient
    @Contextual
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class StatusCredential(
        val id: String,
        val jwt: String,
    )

    override fun updateHolderCredential(vararg values: String): MultiStatusResourceData = copy(
        holderCredential = updateJsonObjectPlaceholders(
            jsonObject = holderCredential,
            placeholder = STATUS_CREDENTIAL_PATH_PLACEHOLDER,
            valueSeparator = PLACEHOLDER_VALUE_SEPARATOR,
            placeholderValue = values
        )
    )

    override fun updateStatusCredential(vararg values: String): StatusResourceData = copy(
        statusCredential = updateStatusCredentialPlaceholder(*values)
    )

    //not nice, but ¯\_(ツ)_/¯
    private fun updateStatusCredentialPlaceholder(vararg values: String): List<StatusCredential> {
        val array = json.encodeToJsonElement<List<StatusCredential>>(statusCredential).jsonArray
        val updated = updateJsonArrayPlaceholders(
            jsonArray = array,
            placeholder = STATUS_CREDENTIAL_PATH_PLACEHOLDER,
            valueSeparator = PLACEHOLDER_VALUE_SEPARATOR,
            placeholderValue = values
        )
        return json.decodeFromJsonElement<List<StatusCredential>>(updated)
    }
}