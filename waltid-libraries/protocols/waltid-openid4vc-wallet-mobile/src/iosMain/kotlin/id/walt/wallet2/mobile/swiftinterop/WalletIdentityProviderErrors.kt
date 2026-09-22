package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.identity.IdentityProviderFailure
import id.walt.wallet2.mobile.identity.IdentityProviderException

/** Preserves a Swift provider's typed failure through Kotlin/Native's NSError boundary. */
public object WalletIdentityProviderErrors {
    /** Raises the corresponding Kotlin exception through the generated throwing Swift entry point.
     * @param failure Stable provider failure category, without secrets or diagnostic messages. */
    @Throws(IdentityProviderException::class)
    public fun raise(failure: IdentityProviderFailure): Nothing = throw IdentityProviderException(failure)
}
