package id.walt.policies2.vc.policies.status.model

import id.walt.policies2.vc.policies.status.Values.BITSTRING_STATUS_LIST
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("discriminator")
sealed class StatusPolicyArgument

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("discriminator")
sealed class StatusPolicyAttribute : StatusPolicyArgument() {
    abstract val value: UInt?
    abstract val values: List<UInt>?
    
    fun getAllowedValues(): List<UInt> = values?.takeIf { it.isNotEmpty() } ?: listOfNotNull(value)
}

@Serializable
@SerialName("w3c")
data class W3CStatusPolicyAttribute(
    override val value: UInt? = null,
    override val values: List<UInt>? = null,
    val purpose: String,
    val type: String,
) : StatusPolicyAttribute() {
    init {
        require(value != null || !values.isNullOrEmpty()) { "Either 'value' or 'values' must be provided" }
    }
}

@Serializable
@SerialName("ietf")
data class IETFStatusPolicyAttribute(
    override val value: UInt? = null,
    override val values: List<UInt>? = null,
) : StatusPolicyAttribute() {
    init {
        require(value != null || !values.isNullOrEmpty()) { "Either 'value' or 'values' must be provided" }
    }
}

@Serializable
@SerialName("w3c-list")
data class W3CStatusPolicyListArguments(
    val list: List<W3CStatusPolicyAttribute>,
) : StatusPolicyArgument() {
    init {
        require(list.isNotEmpty()) { "List cannot be empty" }
        require(list.all { it.type == BITSTRING_STATUS_LIST }) {
            "All entries must be of type $BITSTRING_STATUS_LIST"
        }
    }
}
