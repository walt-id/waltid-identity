package id.walt.crypto2.signum

import at.asitplus.signum.supreme.os.IosKeychainProvider
import at.asitplus.signum.supreme.os.PlatformSigningProviderSigner
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId

class IosSignumKeyBackend : SignumPlatformBackend {
    override val id = ProviderId("ios-keychain-signum")

    override fun supports(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
        spec.isSupportedSignumSpec() &&
            usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY || it == KeyUsage.KEY_AGREEMENT } &&
            (KeyUsage.KEY_AGREEMENT !in usages || spec is KeySpec.Ec) &&
            (KeyUsage.KEY_AGREEMENT in usages) == policy.keyAgreement &&
            (policy.hardware != SignumHardwarePolicy.REQUIRED || spec == KeySpec.Ec(EcCurve.P256))

    override suspend fun create(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey {
        require(supports(spec, usages, policy)) { "iOS Signum backend does not support the requested key and policy" }
        val signer = IosKeychainProvider.createSigningKey(alias) {
            configureSignumKey(spec, usages, policy)
        }.getOrThrow()
        return handle(alias, spec, usages, policy, signer)
    }

    override suspend fun load(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey? {
        val signer = IosKeychainProvider.getSignerForKey(alias).getOrNull() ?: return null
        return handle(alias, spec, usages, policy, signer)
    }

    override suspend fun delete(alias: String) {
        IosKeychainProvider.deleteSigningKey(alias).getOrThrow()
    }

    private fun handle(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
        signer: PlatformSigningProviderSigner<*, *>,
    ): SignumPlatformKey {
        val attestation = signer.toAttestation()
        return SignumPlatformKeyHandle(
            alias = alias,
            spec = spec,
            protectionLevel = policy.effectiveProtection(attestation),
            attestation = attestation,
            authentication = policy.authentication,
            signerFor = { algorithm: SignatureAlgorithm ->
                IosKeychainProvider.getSignerForKey(alias) {
                    configureSignumOperation(algorithm, policy.authentication)
                }.getOrThrow()
            },
            defaultSigner = signer,
            keyAgreementEnabled = KeyUsage.KEY_AGREEMENT in usages && policy.keyAgreement,
        )
    }
}

private fun KeySpec.isSupportedSignumSpec(): Boolean = when (this) {
    is KeySpec.Ec -> curve == EcCurve.P256 || curve == EcCurve.P384 || curve == EcCurve.P521
    is KeySpec.Rsa -> bits == 2048 || bits == 3072 || bits == 4096
    else -> false
}
