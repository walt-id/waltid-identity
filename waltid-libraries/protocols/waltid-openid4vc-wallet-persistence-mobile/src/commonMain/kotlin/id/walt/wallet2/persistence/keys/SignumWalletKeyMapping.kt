package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.signum.SignumInteractionContextUnavailableException
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.crypto2.signum.SignumHardwarePolicy
import id.walt.crypto2.signum.SignumAuthenticationPolicy
import id.walt.crypto2.signum.SignumStoredKeyMetadataException
import id.walt.crypto2.signum.SignumUserCancelledException

/**
 * Translates only the typed Signum failures understood by the built-in mobile adapters.
 * Unknown failures remain provider-specific and cross the adapter boundary unchanged.
 */
internal fun Throwable.toKeyUseAuthorizationException(
    protectedKeyId: String? = null,
    policyMismatchFailure: KeyUseAuthorizationFailure =
        KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
): KeyUseAuthorizationException? = when (this) {
    is SignumInteractionContextUnavailableException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.InteractionContextUnavailable,
            message = "Protected key interaction context is unavailable",
            cause = this,
        )

    is SignumKeyPolicyMismatchException ->
        KeyUseAuthorizationException(
            failure = policyMismatchFailure,
            message = when (policyMismatchFailure) {
                KeyUseAuthorizationFailure.UnsupportedCombination ->
                    "The platform could not enforce the requested key policy"
                else ->
                    "Protected key${protectedKeyId?.let { " '$it'" }.orEmpty()} is unavailable"
            },
            cause = this,
        )

    is SignumKeyInvalidatedException,
    is SignumKeyNotFoundException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
            message = "Protected key${protectedKeyId?.let { " '$it'" }.orEmpty()} is unavailable",
            cause = this,
        )

    is SignumStoredKeyMetadataException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
            message = "Stored managed-key metadata is invalid",
            cause = this,
        )

    is SignumUserCancelledException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.AuthorizationNotCompleted,
            message = "Protected-key authorization was not completed",
            cause = this,
        )

    else -> null
}

/** Wraps protected managed-key signing with the wallet's stable authorization failures. */
internal fun ManagedKey.withWalletAuthorizationMapping(
    authorizationPolicy: KeyUseAuthorizationPolicy,
): ManagedKey {
    if (authorizationPolicy == KeyUseAuthorizationPolicy.None) {
        return this
    }

    val delegate = this
    val storedKey = delegate.storedKey
    return object : ManagedKey {
        override val storedKey = delegate.storedKey
        override val capabilities = delegate.capabilities.copy(
            signer = delegate.capabilities.signer?.let { signer ->
                Signer { data, algorithm ->
                    try {
                        signer.sign(data, algorithm)
                    } catch (cause: Throwable) {
                        throw cause.toKeyUseAuthorizationException(storedKey.id.value) ?: cause
                    }
                }
            },
        )
    }
}

/** Interprets persisted Signum policy only when the complete wallet protected-key shape matches. */
internal fun SignumKeyPolicy.toWalletPolicy(stored: StoredKey.Managed): KeyUseAuthorizationPolicy =
    when (val authentication = authentication) {
        SignumAuthenticationPolicy.None -> KeyUseAuthorizationPolicy.None
        else -> if (
            stored.spec == KeySpec.Ec(EcCurve.P256) &&
            stored.usages == setOf(KeyUsage.SIGN, KeyUsage.VERIFY) &&
            hardware == SignumHardwarePolicy.REQUIRED &&
            authentication.isWalletBiometricCurrentSet()
        ) {
            KeyUseAuthorizationPolicy.BiometricCurrentSet
        } else {
            throw KeyUseAuthorizationException(
                KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
                "Stored Signum key uses an unsupported wallet authorization policy",
            )
        }
    }

private fun SignumAuthenticationPolicy.isWalletBiometricCurrentSet(): Boolean =
    this is SignumAuthenticationPolicy.UserPresence &&
        biometric &&
        !allowNewBiometrics &&
        !deviceCredential &&
        timeoutSeconds == 0
