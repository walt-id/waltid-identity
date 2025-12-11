package id.walt.policies2.vc

import id.walt.policies2.vc.policies.*

object VerificationPolicyManager {

    /**
     * Simple verification policies: policies that don't have to be instantiated (by JSON config)
     * but can simply be referred to by name.
     *
     * E.g. `"signature"` instead of `{"policy": "signature"}`
     *
     * Of course, only sensible for policies WITHOUT configuration required
     * (no mandatory constructor arguments)
     */
    val simpleVerificationPolicies = listOf<CredentialVerificationPolicy2>(
        CredentialSignaturePolicy(),
        ExpirationDatePolicy(),
        NotBeforePolicy(),
        RevocationPolicy(),
    ).associateBy { it.id }

    fun getSimpleVerificationPolicyByName(id: String): CredentialVerificationPolicy2 =
        simpleVerificationPolicies[id]
            ?: throw IllegalArgumentException("Unknown primitive verification policy: type '$id'. Primitive verification policies are policies that do not have any arguments.")

}
