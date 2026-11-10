package id.walt.wallet2.handlers

import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.Signer
import id.waltid.openid4vp.wallet.presentation.ScaPresentationAuthorizer
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import id.waltid.openid4vp.wallet.presentation.ScaPresentation

/**
 * Trusted authorization wiring for a reviewed wallet presentation.
 *
 * The handler supplies the actual key used to sign this proof after resolving the reviewed
 * selection. Methods must have been applied to this attempt or be guaranteed by successful
 * signing with that key. This is not a serializable request option or an authorization cache.
 * The caller retains responsibility for consent and the action's lifetime.
 */
fun interface WalletScaPresentationAuthorizer {
    suspend fun authorize(key: Key, presentation: ScaPresentation): ScaAuthenticationMethods

    /** Authorizes and signs this proof using the resolved holder key, within one native lifetime. */
    suspend fun withAuthorizedSigner(
        key: Key,
        presentation: ScaPresentation,
        operation: suspend (ScaAuthenticationMethods, Signer) -> String,
    ): String = operation(authorize(key, presentation), requireNotNull(key.capabilities.signer))
}

internal fun WalletScaPresentationAuthorizer.forKey(key: Key): ScaPresentationAuthorizer =
    object : ScaPresentationAuthorizer {
        override suspend fun authorize(presentation: ScaPresentation): ScaAuthenticationMethods =
            this@forKey.authorize(key, presentation)

        override suspend fun withAuthorizedSigner(
            presentation: ScaPresentation,
            signer: Signer,
            operation: suspend (ScaAuthenticationMethods, Signer) -> String,
        ): String =
            this@forKey.withAuthorizedSigner(key, presentation, operation)
    }
