package id.walt.wallet2.mobile

import id.walt.wallet2.consent.PaymentConsentMetadata

/** Optional platform capability for issuer-authenticated instructions in system payment confirmation. */
public interface MobileWalletPaymentCredentialRegistry : MobileWalletCredentialRegistry {
    /**
     * Replaces records and their authenticated payment instructions as one registry snapshot.
     * Keys in [paymentMetadata] are revision-bound registry entry IDs. Implementations must not
     * obtain labels from the requesting verifier or treat absent metadata as authenticated consent.
     * [preferredLocales] has the same language preference order as wallet-owned payment review.
     */
    public suspend fun replaceWithPaymentMetadata(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
        paymentMetadata: Map<String, PaymentConsentMetadata>,
        preferredLocales: List<String>,
    ): MobileWalletCredentialRegistrationResult
}
