package id.walt.wallet2.mobile.digitalcredentials

import id.walt.mdoc.readertrust.MdocReaderAuthenticationTrustEvaluator
import id.walt.mdoc.readertrust.ReaderTrustEvaluator

/**
 * OS digital credential integration.
 * @property registry Registry for publishing wallet metadata. The platform factory supplies its
 * native registry when omitted. Pass [UnavailableCredentialRegistry] to disable publication.
 * @property readerTrustEvaluator Trust policy for ISO 18013-7 Annex C reader authentication.
 * @property crossProcessAccess Shared container and Keychain access for a provider extension.
 * @property onRegistrationChanged Called after a credential-set mutation republishes registration
 * metadata. Its failure leaves the committed wallet mutation in place.
 */
public data class DigitalCredentialsConfiguration(
    public val registry: CredentialRegistry? = null,
    public val readerTrustEvaluator: ReaderTrustEvaluator = MdocReaderAuthenticationTrustEvaluator,
    public val crossProcessAccess: WalletAppGroupAccess? = null,
    public val onRegistrationChanged: suspend () -> Unit = {},
) {
    internal fun resolveRegistry(platform: () -> CredentialRegistry): CredentialRegistry =
        registry ?: platform()

    init {
        crossProcessAccess?.let {
            require(it.appGroupIdentifier.isNotBlank() && it.keychainAccessGroup.isNotBlank()) {
                "App Group and Keychain access group must both be nonblank"
            }
        }
    }
}
