package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.signum.SignumInteractionContextUnavailableException
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumStoredKeyMetadataException
import id.walt.crypto2.signum.SignumUserCancelledException

/**
 * Translates only the typed Signum failures understood by the built-in mobile adapters.
 * Unknown failures remain provider-specific and cross the adapter boundary unchanged.
 */
internal fun Throwable.toKeyUseAuthorizationException(
    protectedKeyId: String? = null,
): KeyUseAuthorizationException? = when (this) {
    is SignumInteractionContextUnavailableException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.InteractionContextUnavailable,
            message = "Protected key interaction context is unavailable",
            cause = this,
        )

    is SignumKeyPolicyMismatchException ->
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.UnsupportedCombination,
            message = "The platform could not enforce the requested key policy",
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
