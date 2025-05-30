package id.walt.policies.policies.status

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("discriminator")
sealed class CredentialStatusPolicyArguments

@Serializable
@SerialName("w3c")
data class W3CStatusPolicyArguments(
    val purpose: String,
    val type: String,
    val value: UInt,
) : CredentialStatusPolicyArguments()

@Serializable
@SerialName("w3c-list")
data class W3CStatusPolicyListArguments(
    val list: List<W3CStatusPolicyArguments>,
) : CredentialStatusPolicyArguments() {
    init {
        require(list.all {
            it.type == "BitstringStatusListEntry"
        }) { "Expecting only BitstringStatusListEntry" }
    }
}

@Serializable
@SerialName("ietf")
data class IETFStatusPolicyArguments(
    val value: UInt,
) : CredentialStatusPolicyArguments()