package id.walt.policies2

import id.walt.policies2.policies.CredentialSignaturePolicy
import id.walt.policies2.policies.ExpirationDatePolicy
import id.walt.policies2.policies.NotBeforePolicy
import id.walt.policies2.policies.VerificationPolicy2

object VerificationPolicyManager {

    /** Simple verification policies: policies that don't have to be instantiated (by JSON config),
     * but can simply be referred to by name.
     *
     * E.g. `"signature"` instead of `{"policy": "signature"}`
     *
     * Of course, only sensible for policies WITHOUT configuration required
     * (no mandatory constructor arguments)
     */
    val simpleVerificationPolicies = listOf<VerificationPolicy2>(
        CredentialSignaturePolicy(),
        ExpirationDatePolicy(),
        NotBeforePolicy()
    ).associateBy { it.id }

    fun getSimpleVerificationPolicyByName(id: String): VerificationPolicy2 =
        simpleVerificationPolicies[id] ?: throw IllegalArgumentException("Unknown primitive verification policy: type '$id'. Primitive verification policies are policies that do not have any arguments.")

}
