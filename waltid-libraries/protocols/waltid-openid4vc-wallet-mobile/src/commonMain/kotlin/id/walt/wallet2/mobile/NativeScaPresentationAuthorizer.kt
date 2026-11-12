package id.walt.wallet2.mobile

import id.walt.crypto2.keys.*
import id.walt.wallet2.handlers.WalletScaPresentationAuthorizer
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import id.waltid.openid4vp.wallet.presentation.ScaPresentation

/** Binds payment evidence to native signing, using a fresh single-use authorization for timed keys. */
internal class NativeScaPresentationAuthorizer(
    private val provider: PlatformManagedKeyProvider,
) : WalletScaPresentationAuthorizer {
    override suspend fun authorize(key: Key, presentation: ScaPresentation): ScaAuthenticationMethods =
        evidence(key, presentation, allowTimed = false)

    override suspend fun withAuthorizedSigner(
        key: Key,
        presentation: ScaPresentation,
        operation: suspend (ScaAuthenticationMethods, Signer) -> String,
    ): String {
        val methods = evidence(key, presentation, allowTimed = true)
        val managed = key as ManagedKey
        return if (provider.keyUseAuthorizationPolicy(managed.storedKey) is KeyUseAuthorizationPolicy.BiometricTimedReuse) {
            provider.withFreshBiometricAuthorization(managed.storedKey) { signer -> operation(methods, signer) }
        } else operation(methods, requireNotNull(key.capabilities.signer))
    }

    private suspend fun evidence(key: Key, presentation: ScaPresentation, allowTimed: Boolean): ScaAuthenticationMethods {
        val managed = key as? ManagedKey ?: unavailable("a platform-managed signing key is required")
        if (key.id.value != presentation.holderKeyId || key.spec != KeySpec.Ec(EcCurve.P256) ||
            presentation.signingAlgorithm != "ES256" || KeyUsage.SIGN !in key.usages ||
            key.capabilities.signer == null || key.capabilities.privateKeyExporter != null
        ) unavailable("the selected proof key is not eligible")

        val stored = managed.storedKey
        val policy = provider.keyUseAuthorizationPolicy(stored)
        if (policy != KeyUseAuthorizationPolicy.BiometricCurrentSet &&
            !(allowTimed && policy is KeyUseAuthorizationPolicy.BiometricTimedReuse)) {
            unavailable("biometric-only authentication bound to this payment is required")
        }
        // This restores and validates the actual native entry. Persisted policy alone is not evidence.
        val facts = provider.keyFacts(stored)
        if (facts.origin != KeyOrigin.GENERATED || facts.protection != KeyProtectionLevel.HARDWARE ||
            facts.securityLevel !in setOf(KeySecurityLevel.TRUSTED_ENVIRONMENT, KeySecurityLevel.STRONGBOX, KeySecurityLevel.SECURE_ENCLAVE) ||
            facts.authorizationEvidence !in setOf(KeyAuthorizationEvidence.NATIVE_ATTRIBUTES, KeyAuthorizationEvidence.CREATION_RECORD)
        ) unavailable("native key protection and authorization provenance could not be established")

        // The native key policy or the scoped signer establishes biometrics before proof release.
        // Neither the biometric modality nor a certified WSCD category is exposed by this contract.
        return ScaAuthenticationMethods.PossessionAndInherence(
            possession = ScaAuthenticationMethods.Possession.OTHER,
            inherence = ScaAuthenticationMethods.Inherence.OTHER,
        )
    }

    private fun unavailable(reason: String): Nothing = throw KeyUseAuthorizationException(
        KeyUseAuthorizationFailure.UnsupportedCombination,
        "TS12 authentication evidence is unavailable: $reason",
    )
}
