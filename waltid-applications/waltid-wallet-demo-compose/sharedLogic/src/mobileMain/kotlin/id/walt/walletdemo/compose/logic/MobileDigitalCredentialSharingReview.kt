package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletAnnexCPreview
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialPreview
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialOption
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialRequirement
import id.walt.wallet2.mobile.MobileWalletReaderTrust

/**
 * Maps an OpenID4VP Digital Credentials API preview onto the shared sharing-review model.
 *
 * The preview's `requestId` and the platform request objects stay with the caller: only what the user
 * has to decide about crosses into the review model.
 */
fun MobileWalletDigitalCredentialPreview.toSharingReview(): WalletDemoSharingReview =
    WalletDemoSharingReview(
        request = WalletDemoSharingRequest(
            verifier = WalletDemoSharingVerifier(
                display = request.verifierMetadata?.display?.toDemoMetadataDisplay(),
                // Falling back to the origin keeps the Verifier named by something authenticated.
                // An unsigned DC API request has no trusted `client_id`, so there is nothing else
                // truthful to head this section with.
                fallbackName = verifiedOrigin,
                verifiedOrigin = verifiedOrigin,
                details = listOfNotNull(
                    request.verifierMetadata?.let { WalletDemoSharingDetail("Client URI", it.clientUri, it.clientUri) },
                    request.verifierMetadata?.let { WalletDemoSharingDetail("Privacy policy", it.policyUri, it.policyUri) },
                    request.verifierMetadata?.let {
                        WalletDemoSharingDetail("Terms of service", it.termsOfServiceUri, it.termsOfServiceUri)
                    },
                ),
            ),
            // The OpenID4VP DC API has no reader authentication, so the review offers no reader
            // section rather than one reporting an absent reader.
            readerTrust = readerTrust.toDemoReaderTrust(),
            responseProtection = if (request.responseMode == DC_API_JWT_RESPONSE_MODE) {
                WalletDemoSharingResponseProtection.Encrypted(WalletDemoSharingEncryptionMechanism.DcApiJwt)
            } else {
                WalletDemoSharingResponseProtection.None
            },
            transactionData = request.transactionData.toDemoTransactionDataGroups(),
            technicalDetails = listOf(
                WalletDemoSharingDetail("Protocol", protocol),
                WalletDemoSharingDetail("Client ID", request.clientId),
                WalletDemoSharingDetail("Response mode", request.responseMode),
                WalletDemoSharingDetail("Nonce", request.nonce),
            ),
        ),
        credentialOptions = credentialOptions.map { it.toDemoCredentialOption() },
        credentialRequirements = credentialRequirements.map { it.toDemoCredentialRequirement() },
    )

/**
 * Maps an ISO 18013-7 Annex C preview onto the shared sharing-review model.
 *
 * Annex C carries no OpenID4VP request parameters, so the review gets no client ID, state or
 * response URI at all. The requested documents and elements are reviewed through the same credential
 * and disclosure components every other transport uses, which is what makes them comparable.
 */
fun MobileWalletAnnexCPreview.toSharingReview(): WalletDemoSharingReview =
    WalletDemoSharingReview(
        request = WalletDemoSharingRequest(
            verifier = WalletDemoSharingVerifier(
                fallbackName = verifiedOrigin,
                verifiedOrigin = verifiedOrigin,
            ),
            readerTrust = readerTrust.toDemoReaderTrust(),
            // Annex C always session-encrypts the device response; there is no unencrypted variant to
            // report, so this states the mechanism rather than asking whether encryption was requested.
            responseProtection = WalletDemoSharingResponseProtection.Encrypted(
                mechanism = WalletDemoSharingEncryptionMechanism.AnnexCHpke,
                keyManagementAlgorithm = "DHKEM(P-256, HKDF-SHA256)",
                contentEncryptionAlgorithm = "AES-128-GCM",
            ),
            technicalDetails = listOf(
                WalletDemoSharingDetail(
                    label = "Requested documents",
                    value = parsedRequest.documents.joinToString(", ") { it.docType }.takeIf { it.isNotBlank() },
                ),
            ),
        ),
        credentialOptions = credentialOptions.map { it.toDemoCredentialOption() },
        // Annex C requests every listed document, so each requested document is its own requirement
        // and Share stays disabled until one credential is chosen for each.
        credentialRequirements = credentialOptions
            .map { it.queryId }
            .distinct()
            .map { queryId -> WalletDemoPresentationCredentialRequirement(options = listOf(listOf(queryId))) },
    )

/**
 * Translates SDK reader-trust states into review states.
 *
 * [MobileWalletReaderTrust.NotApplicable] becomes null rather than a state, because "this protocol has
 * no reader authentication" is answered by omitting the section, not by rendering one.
 */
private fun MobileWalletReaderTrust.toDemoReaderTrust(): WalletDemoReaderTrust? = when (this) {
    MobileWalletReaderTrust.NotApplicable -> null
    MobileWalletReaderTrust.NotAuthenticated -> WalletDemoReaderTrust.NotAuthenticated
    MobileWalletReaderTrust.PendingRawRequest -> WalletDemoReaderTrust.PendingVerification
    is MobileWalletReaderTrust.Untrusted -> WalletDemoReaderTrust.Untrusted(reason)
    is MobileWalletReaderTrust.Trusted -> WalletDemoReaderTrust.Trusted(certificateSubject)
}

private fun MobileWalletPresentationCredentialOption.toDemoCredentialOption(): WalletDemoPresentationCredentialOption =
    WalletDemoPresentationCredentialOption(
        queryId = queryId,
        credentialId = credentialId,
        multiple = multiple,
        label = CredentialDisplayNameResolver.resolve(
            label = label,
            format = format,
            credentialDataJson = credentialDataJson,
        ),
        issuer = issuer,
        subject = subject,
        format = format,
        credentialDataJson = credentialDataJson,
        disclosures = disclosures.map { disclosure ->
            WalletDemoPresentationDisclosure(
                label = CredentialDisplayVocabulary.disclosureLabel(disclosure.name, disclosure.path),
                path = disclosure.path,
                valueJson = disclosure.valueJson,
                displayValue = disclosure.displayValue,
                selectivelyDisclosable = disclosure.selectivelyDisclosable,
                required = disclosure.required,
                selectable = disclosure.selectable,
            )
        },
    )

private fun MobileWalletPresentationCredentialRequirement.toDemoCredentialRequirement():
    WalletDemoPresentationCredentialRequirement =
    WalletDemoPresentationCredentialRequirement(options = options)

/** Serialized OpenID4VP response mode that returns a JWE through the Digital Credentials API. */
private const val DC_API_JWT_RESPONSE_MODE = "dc_api.jwt"
