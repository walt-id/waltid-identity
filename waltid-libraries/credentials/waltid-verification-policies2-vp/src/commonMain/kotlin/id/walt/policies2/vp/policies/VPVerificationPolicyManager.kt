package id.walt.policies2.vp.policies

object VPVerificationPolicyManager {

    val simpleDcSdJwtPolicies: Array<DcSdJwtVPPolicy> = arrayOf(
        AudienceCheckSdJwtVPPolicy(),
        KbJwtSignatureSdJwtVPPolicy(),
        NonceCheckSdJwtVPPolicy(),
        SdHashCheckSdJwtVPPolicy()
    )
    val defaultDcSdJwtPolicies = simpleDcSdJwtPolicies.toList()

    val simpleJwtVcJsonPolicies: Array<JwtVcJsonVPPolicy> = arrayOf(
        AudienceCheckJwtVcJsonVPPolicy(),
        NonceCheckJwtVcJsonVPPolicy(),
        SignatureJwtVcJsonVPPolicy()
    )
    val defaultJwtVcJsonPolicies = simpleJwtVcJsonPolicies.toList()

    val simpleMsoMdocPolicies: Array<MdocVPPolicy> = arrayOf(
        DeviceAuthMdocVpPolicy(),
        DeviceKeyAuthMdocVpPolicy(),
        IssuerAuthMdocVpPolicy(),
        IssuerSignedDataMdocVpPolicy(),
        MsoVerificationMdocVpPolicy()
    )
    val defaultMsoMdocPolicies = simpleMsoMdocPolicies.toList()


    /**
     * Simple verification policies: policies that don't have to be instantiated (by JSON config)
     * but can simply be referred to by name.
     *
     * E.g. `"signature"` instead of `{"policy": "signature"}`
     *
     * Of course, only sensible for policies WITHOUT configuration required
     * (no mandatory constructor arguments)
     */
    val simpleVerificationPolicies: Map<String, VPPolicy2> = listOf(
        *simpleDcSdJwtPolicies,
        *simpleJwtVcJsonPolicies,
        *simpleMsoMdocPolicies
    ).associateBy { it.id }

    fun getSimpleVerificationPolicyByName(id: String): VPPolicy2 =
        simpleVerificationPolicies[id]
            ?: throw IllegalArgumentException("Unknown primitive verification policy: type '$id'. Primitive verification policies are policies that do not have any arguments.")

    fun isSimplePolicy(id: String): Boolean =
        simpleVerificationPolicies.containsKey(id)

}
