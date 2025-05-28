package id.walt.policies.policies.status

import kotlinx.serialization.Serializable

@Serializable
sealed class CredentialStatusPolicyArguments {
    abstract val type: String
    abstract val expectedValue: String
}

@Serializable
data class W3CStatusPolicyArguments(
    val purpose: String,
    override val type: String,
    override val expectedValue: String,
) : CredentialStatusPolicyArguments()

@Serializable
data class IETFStatusPolicyArguments(
    override val type: String,
    override val expectedValue: String,
) : CredentialStatusPolicyArguments()