package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.CredentialDisplayNormalizer
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosure
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoReaderTrust
import id.walt.walletdemo.compose.logic.WalletDemoSharingDetail
import id.walt.walletdemo.compose.logic.WalletDemoSharingEncryptionMechanism
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequester
import id.walt.walletdemo.compose.logic.WalletDemoSharingResponseProtection
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoTransactionDataItem

/**
 * Reviews and credential options the sharing-review tests are written against.
 *
 * Kept apart from the scenarios so a platform-specific test can drive the same screen against the
 * same request. Back handling is the case that needs it: only the platform that has a back gesture
 * can dispatch one, while what the gesture must do to a review is not platform-specific at all.
 */
internal object WalletDemoSharingReviewFixtures {
    const val REQUIRED_DISCLOSURE_PATH: String = "org.iso.18013.5.1/given_name"
    const val OPTIONAL_DISCLOSURE_PATH: String = "org.iso.18013.5.1/portrait"

    /** An unsigned `dc_api.jwt` request: a verified origin, transaction data, and no reader auth. */
    fun digitalCredentialReview(
        credentialOptions: List<WalletDemoPresentationCredentialOption> = listOf(credentialOption()),
    ): WalletDemoSharingReview = WalletDemoSharingReview(
        request = WalletDemoSharingRequest(
            requester = WalletDemoSharingRequester(
                fallbackName = "https://verifier.example",
                verifiedOrigin = "https://verifier.example",
            ),
            responseProtection = WalletDemoSharingResponseProtection.Encrypted(
                mechanism = WalletDemoSharingEncryptionMechanism.DcApiJwt,
            ),
            transactionData = CredentialDisplayNormalizer.transactionDataGroups(
                listOf(
                    WalletDemoTransactionDataItem(
                        type = "org.waltid.transaction-data.payment-authorization",
                        displayName = "Payment Authorization",
                        credentialQueryIds = listOf("pid"),
                        supportedFields = listOf("amount", "currency", "payee"),
                        rawJson = """{"amount":"42.00","currency":"EUR","payee":"ACME Corp"}""",
                        detailsJson = """{"amount":"42.00","currency":"EUR","payee":"ACME Corp"}""",
                    ),
                )
            ),
            technicalDetails = listOf(
                WalletDemoSharingDetail("Protocol", "openid4vp-v1-unsigned"),
                WalletDemoSharingDetail("Response mode", "dc_api.jwt"),
            ),
        ),
        credentialOptions = credentialOptions,
    )

    /**
     * An Annex C request, which does have reader authentication and HPKE session encryption.
     *
     * Every distinct query gets its own requirement, so a request for two documents cannot be
     * answered with one.
     */
    fun annexCReview(
        readerTrust: WalletDemoReaderTrust,
        credentialOptions: List<WalletDemoPresentationCredentialOption> = listOf(
            credentialOption(queryId = "org.iso.18013.5.1.mDL"),
        ),
    ): WalletDemoSharingReview = WalletDemoSharingReview(
        request = WalletDemoSharingRequest(
            requester = WalletDemoSharingRequester(
                fallbackName = "https://verifier.example",
                verifiedOrigin = "https://verifier.example",
            ),
            readerTrust = readerTrust,
            responseProtection = WalletDemoSharingResponseProtection.Encrypted(
                mechanism = WalletDemoSharingEncryptionMechanism.AnnexCHpke,
                keyManagementAlgorithm = "DHKEM(P-256, HKDF-SHA256)",
                contentEncryptionAlgorithm = "AES-128-GCM",
            ),
        ),
        credentialOptions = credentialOptions,
        credentialRequirements = credentialOptions.map { option -> option.queryId }
            .distinct()
            .map { queryId -> WalletDemoPresentationCredentialRequirement(options = listOf(listOf(queryId))) },
    )

    fun credentialOption(
        queryId: String = "pid",
        credentialId: String = "credential-1",
        label: String = "Driving licence",
        disclosures: List<WalletDemoPresentationDisclosure> = listOf(requiredDisclosure()),
    ): WalletDemoPresentationCredentialOption = WalletDemoPresentationCredentialOption(
        queryId = queryId,
        credentialId = credentialId,
        label = label,
        issuer = "Test Issuer",
        format = "mso_mdoc",
        credentialDataJson = "{}",
        disclosures = disclosures,
    )

    /** A disclosure the request requires, so the wallet has no choice to offer over it. */
    fun requiredDisclosure(): WalletDemoPresentationDisclosure = WalletDemoPresentationDisclosure(
        label = "Given name",
        path = REQUIRED_DISCLOSURE_PATH,
        valueJson = "\"Ada\"",
        displayValue = "Ada",
        selectivelyDisclosable = false,
    )

    /**
     * A disclosure the credential format can withhold and the request did not insist on, which is the
     * only shape [WalletDemoPresentationDisclosure.selectable] is true for and so the only one the
     * review renders a toggle beside.
     */
    fun optionalDisclosure(): WalletDemoPresentationDisclosure = WalletDemoPresentationDisclosure(
        label = "Portrait",
        path = OPTIONAL_DISCLOSURE_PATH,
        valueJson = "\"...\"",
        displayValue = "Photo",
        selectivelyDisclosable = true,
        required = false,
    )

    fun disclosureSelection(
        option: WalletDemoPresentationCredentialOption,
        path: String,
    ): WalletDemoPresentationDisclosureSelection = WalletDemoPresentationDisclosureSelection(
        queryId = option.queryId,
        credentialId = option.credentialId,
        path = path,
    )
}
