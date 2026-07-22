@file:Suppress("DEPRECATION")

package id.walt.policies2.vp.policies

object VPVerificationPolicyManager {

    val simpleDcSdJwtPolicies: Array<DcSdJwtVPPolicy> = arrayOf(
        AudienceCheckSdJwtVPPolicy(),
        KbJwtSignatureSdJwtVPPolicy(),
        NonceCheckSdJwtVPPolicy(),
        SdHashCheckSdJwtVPPolicy(),
        KbJwtIatCheckSdJwtVPPolicy(),
        ExpCheckSdJwtVPPolicy(),
        NbfCheckSdJwtVPPolicy(),
        TransactionDataHashCheckSdJwtVPPolicy(),
    )
    val defaultDcSdJwtPolicies = simpleDcSdJwtPolicies.toList()

    val simpleJwtVcJsonPolicies: Array<JwtVcJsonVPPolicy> = arrayOf(
        AudienceCheckJwtVcJsonVPPolicy(),
        NonceCheckJwtVcJsonVPPolicy(),
        SignatureJwtVcJsonVPPolicy(),
        ExpCheckJwtVcJsonVPPolicy(),
        NbfCheckJwtVcJsonVPPolicy(),
    )
    val defaultJwtVcJsonPolicies = simpleJwtVcJsonPolicies.toList()

    val simpleMsoMdocPolicies: Array<MdocVPPolicy> = arrayOf(
        DeviceAuthMdocVpPolicy(),
        DeviceKeyAuthMdocVpPolicy(),
        TransactionDataMdocVpPolicy(),
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
        *simpleMsoMdocPolicies,
        TransactionDataHashesVPPolicy(),
    ).associateBy { it.id }

    fun getSimpleVerificationPolicyByName(id: String): VPPolicy2 =
        simpleVerificationPolicies[id]
            ?: throw IllegalArgumentException("Unknown primitive verification policy: type '$id'. Primitive verification policies are policies that do not have any arguments.")

    fun isSimplePolicy(id: String): Boolean =
        simpleVerificationPolicies.containsKey(id)

    fun shouldSerializeAsSimple(policy: VPPolicy2): Boolean = when (policy) {
        is SignatureJwtVcJsonVPPolicy -> policy.hasDefaultAlgorithmConfiguration
        is KbJwtSignatureSdJwtVPPolicy -> policy.hasDefaultAlgorithmConfiguration
        is KbJwtIatCheckSdJwtVPPolicy -> policy.maxAgeMinutes == 5L
        is ExpCheckSdJwtVPPolicy -> policy.clockSkewSeconds == 2L
        is NbfCheckSdJwtVPPolicy -> policy.clockSkewSeconds == 2L
        is ExpCheckJwtVcJsonVPPolicy -> policy.clockSkewSeconds == 2L
        is NbfCheckJwtVcJsonVPPolicy -> policy.clockSkewSeconds == 2L
        is MsoVerificationMdocVpPolicy -> !policy.strictEtsiPrecision
        else -> isSimplePolicy(policy.id)
    }

}
