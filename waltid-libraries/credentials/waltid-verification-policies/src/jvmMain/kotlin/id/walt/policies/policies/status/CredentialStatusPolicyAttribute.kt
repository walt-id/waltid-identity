package id.walt.policies.policies.status

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

private const val bistringStatusListEntry = "BitstringStatusListEntry"

sealed class CredentialStatusPolicyArgument

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("discriminator")
sealed class CredentialStatusPolicyAttribute : CredentialStatusPolicyArgument() {
    abstract val value: UInt
}

@Serializable
@SerialName("w3c")
data class W3CCredentialStatusPolicyAttribute(
    override val value: UInt,
    val purpose: String,
    val type: String,
) : CredentialStatusPolicyAttribute()

@Serializable
@SerialName("ietf")
data class IETFCredentialStatusPolicyAttribute(
    override val value: UInt,
) : CredentialStatusPolicyAttribute()

@Serializable
@SerialName("w3c-list")
data class W3CStatusPolicyListArguments(
    val list: List<W3CCredentialStatusPolicyAttribute>,
) : CredentialStatusPolicyArgument() {
    init {
        require(list.isNotEmpty()) { "List cannot be empty" }
        require(list.all { it.type == bistringStatusListEntry }) {
            "All entries must be of type $bistringStatusListEntry"
        }
    }
}