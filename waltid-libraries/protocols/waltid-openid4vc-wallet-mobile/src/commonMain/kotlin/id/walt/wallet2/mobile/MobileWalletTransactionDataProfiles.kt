package id.walt.wallet2.mobile

/**
 * Well-known OpenID4VP `transaction_data` profiles that wallet apps can opt into.
 *
 * [MobileWalletConfig.transactionDataProfiles] stays empty by default. Passing [all] accepts these
 * types before preview or submit. Apps that support a subset should pass only those entries.
 */
public object MobileWalletTransactionDataProfiles {
    /** walt.id payment-authorization type used by the public demo verifier. */
    public val paymentAuthorization: MobileWalletTransactionDataProfile = MobileWalletTransactionDataProfile(
        type = "org.waltid.transaction-data.payment-authorization",
        displayName = "Payment Authorization",
        fields = listOf("merchant_name", "amount", "currency"),
    )

    /** walt.id account-access type used by the public demo verifier. */
    public val accountAccess: MobileWalletTransactionDataProfile = MobileWalletTransactionDataProfile(
        type = "org.waltid.transaction-data.account-access",
        displayName = "Account Access",
        fields = listOf("account_identifier", "access_scope"),
    )

    /** EUDI TS-12 SCA payment type. The Credential Manager matcher reads the nested `payload`. */
    public val scaPayment: MobileWalletTransactionDataProfile = MobileWalletTransactionDataProfile(
        type = "urn:eudi:sca:payment:1",
        displayName = "SCA Payment",
        fields = listOf("payload"),
    )

    /** Interop identifier used by some OpenID4VP payment-card verifiers. */
    public val paymentCard: MobileWalletTransactionDataProfile = MobileWalletTransactionDataProfile(
        type = "payment_card",
        displayName = "Payment Card",
        fields = listOf("merchant_name", "amount"),
    )

    /** Every well-known profile in this catalog. */
    public val all: List<MobileWalletTransactionDataProfile> = listOf(
        paymentAuthorization,
        accountAccess,
        scaPayment,
        paymentCard,
    )
}
