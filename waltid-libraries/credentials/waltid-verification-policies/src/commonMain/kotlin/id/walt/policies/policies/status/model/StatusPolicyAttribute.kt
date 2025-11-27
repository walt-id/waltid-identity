package id.walt.policies.policies.status.model

import id.walt.policies.policies.status.Values.BITSTRING_STATUS_LIST
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
    abstract val value: UInt
}

@Serializable
@SerialName("w3c")
data class W3CStatusPolicyAttribute(
    override val value: UInt,
    val purpose: String,
    val type: String,
) : StatusPolicyAttribute()

@Serializable
@SerialName("ietf")
data class IETFStatusPolicyAttribute(
    override val value: UInt,
) : StatusPolicyAttribute()

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