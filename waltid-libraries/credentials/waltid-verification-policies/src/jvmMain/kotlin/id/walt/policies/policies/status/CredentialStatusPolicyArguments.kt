package id.walt.policies.policies.status

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class CredentialStatusPolicyArguments {
    abstract val value: String
}

@Serializable
@SerialName("w3c")
data class W3CStatusPolicyArguments(
    val purpose: String,
    val type: String,
    override val value: String,
) : CredentialStatusPolicyArguments()

@Serializable
@SerialName("ietf")
data class IETFStatusPolicyArguments(
    override val value: String,
) : CredentialStatusPolicyArguments()