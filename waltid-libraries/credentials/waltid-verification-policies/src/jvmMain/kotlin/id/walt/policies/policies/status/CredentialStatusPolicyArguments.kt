package id.walt.policies.policies.status

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("discriminator")
sealed class CredentialStatusPolicyArguments {
    abstract val value: UInt
}

@Serializable
@SerialName("w3c")
data class W3CStatusPolicyArguments(
    val purpose: String,
    val type: String,
    override val value: UInt,
) : CredentialStatusPolicyArguments()

@Serializable
@SerialName("ietf")
data class IETFStatusPolicyArguments(
    override val value: UInt,
) : CredentialStatusPolicyArguments()