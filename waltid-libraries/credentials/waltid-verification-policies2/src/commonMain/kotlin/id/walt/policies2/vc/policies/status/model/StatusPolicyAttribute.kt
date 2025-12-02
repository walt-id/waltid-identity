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
sealed class StatusPolicyAttribute : id.walt.policies2.vc.policies.status.model.StatusPolicyArgument() {
    abstract val value: UInt
}

@Serializable
@SerialName("w3c")
data class W3CStatusPolicyAttribute(
    override val value: UInt,
    val purpose: String,
    val type: String,
) : id.walt.policies2.vc.policies.status.model.StatusPolicyAttribute()

@Serializable
@SerialName("ietf")
data class IETFStatusPolicyAttribute(
    override val value: UInt,
) : id.walt.policies2.vc.policies.status.model.StatusPolicyAttribute()

@Serializable
@SerialName("w3c-list")
data class W3CStatusPolicyListArguments(
    val list: List<id.walt.policies2.vc.policies.status.model.W3CStatusPolicyAttribute>,
) : id.walt.policies2.vc.policies.status.model.StatusPolicyArgument() {
    init {
        require(list.isNotEmpty()) { "List cannot be empty" }
        require(list.all { it.type == BITSTRING_STATUS_LIST }) {
            "All entries must be of type $BITSTRING_STATUS_LIST"
        }
    }
}
