package id.walt.crypto2.signum

import android.os.Build
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import at.asitplus.signum.supreme.os.AndroidKeystoreSigner
import at.asitplus.signum.supreme.os.AndroidKeyStoreProvider
import at.asitplus.signum.supreme.os.PlatformSigningProviderSigner
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId

public class AndroidSignumKeyBackend(
    private val interactionContextProvider: () -> FragmentActivity? = { null },
) : SignumPlatformBackend {
    override val id = ProviderId("android-keystore-signum")

    override fun supports(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
        spec.isSupportedSignumSpec() &&
            usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY || it == KeyUsage.KEY_AGREEMENT } &&
            (KeyUsage.KEY_AGREEMENT !in usages || spec is KeySpec.Ec) &&
            (KeyUsage.KEY_AGREEMENT in usages) == policy.keyAgreement

    override suspend fun create(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey {
        require(supports(spec, usages, policy)) { "Android Signum backend does not support the requested key and policy" }
        val signer = AndroidKeyStoreProvider.createSigningKey(alias) {
            configureSignumKey(spec, usages, policy)
        }.getOrThrow()
        try {
            validateNativePolicy(signer, policy, alias)
        } catch (cause: Throwable) {
            try {
                delete(alias)
            } catch (cleanupFailure: Throwable) {
                cause.addSuppressed(cleanupFailure)
            }
            throw cause
        }
        return handle(alias, spec, usages, policy, signer)
    }

    override suspend fun load(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey? {
        val signer = AndroidKeyStoreProvider.getSignerForKey(alias).getOrElse { failure ->
            throw failure.mapSignumFailure(alias)
        }
        validateNativePolicy(signer, policy, alias)
        return handle(alias, spec, usages, policy, signer)
    }

    override suspend fun delete(alias: String) {
        AndroidKeyStoreProvider.deleteSigningKey(alias).getOrElse { failure ->
            val mapped = failure.mapSignumFailure(alias)
            if (mapped is SignumKeyNotFoundException) return
            throw mapped
        }
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
                val interactionContext = policy.authentication.takeIf { it.isBiometricCurrentSet() }
                    ?.let { requireInteractionContext(alias) }
                AndroidKeyStoreProvider.getSignerForKey(alias) {
                    configureSignumOperation(algorithm, policy.authentication)
                    if (interactionContext != null) {
                        unlockPrompt {
                            allowedAuthenticators = BIOMETRIC_STRONG
                            activity = interactionContext
                        }
                    }
                }.getOrElse { failure ->
                    throw failure.mapSignumFailure(alias)
                }
            },
            defaultSigner = signer,
            keyAgreementEnabled = KeyUsage.KEY_AGREEMENT in usages && policy.keyAgreement,
        )
    }

    private fun validateNativePolicy(
        signer: PlatformSigningProviderSigner<*, *>,
        policy: SignumKeyPolicy,
        alias: String,
    ) {
        if (!policy.authentication.isBiometricCurrentSet()) return
        val androidSigner = signer as? AndroidKeystoreSigner
            ?: throw SignumKeyPolicyMismatchException(alias, "the native signer is not Android Keystore-backed")
        val info = androidSigner.keyInfo
        if (!info.isUserAuthenticationRequired ||
            info.userAuthenticationValidityDurationSeconds > 0 ||
            !info.isInvalidatedByBiometricEnrollment
        ) {
            throw SignumKeyPolicyMismatchException(
                alias,
                "the native key does not require biometric authentication for every use",
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            info.userAuthenticationType != KeyProperties.AUTH_BIOMETRIC_STRONG
        ) {
            throw SignumKeyPolicyMismatchException(alias, "the native key does not require BIOMETRIC_STRONG")
        }
    }

    private fun requireInteractionContext(alias: String): FragmentActivity {
        val activity = interactionContextProvider()
        if (activity == null || activity.isFinishing || activity.isDestroyed || activity.isChangingConfigurations ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            throw SignumInteractionContextUnavailableException(
                "A resumed FragmentActivity is required to use protected Signum key $alias",
            )
        }
        return activity
    }
}

private fun Throwable.mapSignumFailure(alias: String): Throwable {
    val causes = generateSequence(this) { it.cause }.toList()
    return when {
        causes.any { it is android.security.keystore.KeyPermanentlyInvalidatedException } ->
            SignumKeyInvalidatedException(alias, this)
        causes.any { it is NoSuchElementException } -> SignumKeyNotFoundException(alias, this)
        else -> this
    }
}

private fun KeySpec.isSupportedSignumSpec(): Boolean = when (this) {
    is KeySpec.Ec -> curve == EcCurve.P256 || curve == EcCurve.P384 || curve == EcCurve.P521
    is KeySpec.Rsa -> bits == 2048 || bits == 3072 || bits == 4096
    else -> false
}
