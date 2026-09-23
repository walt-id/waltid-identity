import Foundation
import WalletDemoSharingUI
import WalletSDK
import XCTest

/// What the shared sharing review says about a request, for each transport that can produce one.
///
/// Mapping tests rather than rendering tests: the review model decides which sections exist, what they
/// claim and which actions are offered, so it is where a transport could start describing a request as
/// something it is not.
final class SharingReviewModelTests: XCTestCase {

    // MARK: - Requester identity

    func testNormalOpenIDReviewIsHeadedByVerifierMetadataAndNotByAVerifiedOrigin() {
        let review = openIDPreview(
            verifierMetadata: VerifierMetadata(
                display: MetadataDisplay(name: "Example Verifier", locale: "en", logoURI: nil, logoAltText: nil),
                clientURI: "https://verifier.example",
                policyURI: "https://verifier.example/privacy",
                termsOfServiceURI: nil
            )
        ).sharingReview()

        let requester = try? XCTUnwrap(review.request.requester)
        XCTAssertEqual(requester?.display?.name, "Example Verifier")
        XCTAssertEqual(
            requester?.verifiedOrigin,
            nil,
            "A plain OpenID4VP request has no platform-verified origin, and claiming one would present a self-asserted client ID as authenticated"
        )
        XCTAssertEqual(
            requester?.details.map(\.label),
            ["Client URI", "Privacy policy", "Terms of service"]
        )
        XCTAssertEqual(requester?.details.first { $0.label == "Privacy policy" }?.linkURI, "https://verifier.example/privacy")
    }

    func testARequestWithoutVerifierMetadataGetsNoRequesterSection() {
        let review = openIDPreview(verifierMetadata: nil).sharingReview()

        XCTAssertNil(
            review.request.requester,
            "An empty requester section would read as a named requester whose name happens to be missing"
        )
    }

    func testVerifierMetadataCarryingNothingUsableIsTreatedAsAbsent() {
        let review = openIDPreview(
            verifierMetadata: VerifierMetadata(
                display: MetadataDisplay(name: "   ", locale: nil, logoURI: nil, logoAltText: nil),
                clientURI: nil,
                policyURI: nil,
                termsOfServiceURI: nil
            )
        ).sharingReview()

        XCTAssertNil(review.request.requester)
    }

    func testAnnexCReviewIsHeadedByTheVerifiedOriginTheWalletWillBindTheResponseTo() {
        let review = annexCPreview(verifiedOrigin: "https://reader.example").sharingReview()
        let requester = try? XCTUnwrap(review.request.requester)

        XCTAssertEqual(requester?.fallbackName, "https://reader.example")
        XCTAssertEqual(
            requester?.verifiedOrigin,
            "https://reader.example",
            "The origin is the only authenticated fact about an Annex C caller, so it must stay marked as verified"
        )
        XCTAssertNil(
            requester?.display,
            "Annex C carries no verifier display metadata, and inventing one would name a caller the request never named"
        )
    }

    func testTheReviewDoesNotReinterpretTheOriginTheKMPCanonicalizerReturned() {
        let review = annexCPreview(verifiedOrigin: "https://Reader.Example:443/").sharingReview()

        XCTAssertEqual(
            review.request.requester?.verifiedOrigin,
            "https://Reader.Example:443/",
            "Canonicalization belongs to the shared KMP layer; normalizing again here could disagree with what the verifier bound its request to"
        )
    }

    func testAnOriginThatHeadsTheRequesterSectionIsCaptionedAsVerifiedAndNotRepeated() {
        let requester = annexCPreview(verifiedOrigin: "https://reader.example").sharingReview().request.requester

        XCTAssertEqual(requester?.identityName, "https://reader.example")
        XCTAssertEqual(
            requester?.identityNameCaption,
            SharingRequester.verifiedOriginLabel,
            "An uncaptioned heading reads as one more self-asserted requester claim, and the origin is the only authenticated one"
        )
        XCTAssertEqual(
            requester?.detailRows.filter { $0.value?.isEmpty == false }.map(\.label),
            [],
            "The origin already heads the section, so a labelled row repeating it would read as a second, independent requester fact"
        )
    }

    func testAnOriginBesideSelfAssertedMetadataIsALabelledRowRatherThanACaption() {
        let requester = SharingRequester(
            display: MetadataDisplay(name: "Example Verifier", locale: "en", logoURI: nil, logoAltText: nil),
            fallbackName: "https://verifier.example",
            verifiedOrigin: "https://verifier.example",
            details: [SharingDetail(label: "Privacy policy", value: "https://verifier.example/privacy")]
        )

        XCTAssertEqual(requester.identityName, "Example Verifier")
        XCTAssertNil(
            requester.identityNameCaption,
            "The heading here is self-asserted metadata, and captioning it as verified would present it as authenticated"
        )
        XCTAssertEqual(
            requester.detailRows.map { [$0.label, $0.value ?? ""] },
            [
                [SharingRequester.verifiedOriginLabel, "https://verifier.example"],
                ["Privacy policy", "https://verifier.example/privacy"],
            ],
            "The origin stays visible under its own label: a review showing only the name a request asked to be called would hide the one verified fact"
        )
    }

    // MARK: - Reader trust

    func testAProtocolWithoutReaderAuthenticationGetsNoReaderTrustState() {
        XCTAssertNil(
            openIDPreview().sharingReview().request.readerTrust,
            "OpenID4VP has no reader to be trusted or untrusted, so any state here would describe something the request does not contain"
        )
        XCTAssertNil(
            ReaderTrust.notApplicable.sharingReaderTrust(),
            "notApplicable is the absence of the concept, not a trust outcome"
        )
    }

    func testAnUnauthenticatedReaderIsReportedAsAnonymousRatherThanAsAFailure() {
        XCTAssertEqual(ReaderTrust.notAuthenticated.sharingReaderTrust(), .notAuthenticated)
        XCTAssertEqual(
            annexCPreview(readerTrust: .notAuthenticated).sharingReview().request.readerTrust,
            .notAuthenticated
        )
    }

    func testPendingReaderAuthenticationIsReportedAsStillToBeCheckedNotAsUntrusted() {
        XCTAssertEqual(
            ReaderTrust.pendingRawRequest.sharingReaderTrust(),
            .pendingVerification,
            "Apple withholding the raw request until consent is the normal two-stage flow, not a trust outcome"
        )
        XCTAssertNotEqual(ReaderTrust.pendingRawRequest.sharingReaderTrust(), .notAuthenticated)
    }

    func testAnUntrustedReaderCarriesTheTrustPolicyReasonRatherThanASignatureVerdict() {
        XCTAssertEqual(
            ReaderTrust.untrusted(reason: "No configured trust anchor").sharingReaderTrust(),
            .untrusted(reason: "No configured trust anchor")
        )
        XCTAssertEqual(
            annexCPreview(readerTrust: .untrusted(reason: "No configured trust anchor"))
                .sharingReview().request.readerTrust,
            .untrusted(reason: "No configured trust anchor")
        )
    }

    func testATrustedReaderCarriesTheIdentityTheWalletCanVouchFor() {
        XCTAssertEqual(
            ReaderTrust.trusted(certificateSubject: "CN=Reader").sharingReaderTrust(),
            .trusted(readerIdentity: "CN=Reader")
        )
        XCTAssertEqual(
            annexCPreview(readerTrust: .trusted(certificateSubject: "CN=Reader"))
                .sharingReview().request.readerTrust,
            .trusted(readerIdentity: "CN=Reader")
        )
    }

    func testEveryReaderTrustStateIsDistinctSoTheUserCanTellThemApart() {
        let states: [SharingReaderTrust?] = [
            ReaderTrust.notApplicable.sharingReaderTrust(),
            ReaderTrust.notAuthenticated.sharingReaderTrust(),
            ReaderTrust.pendingRawRequest.sharingReaderTrust(),
            ReaderTrust.untrusted(reason: "no anchor").sharingReaderTrust(),
            ReaderTrust.trusted(certificateSubject: "CN=Reader").sharingReaderTrust(),
        ]

        XCTAssertEqual(Set(states.map(String.init(describing:))).count, states.count)
    }

    // MARK: - Response protection

    func testAnUnencryptedOpenIDResponseIsReportedAsUnencryptedRatherThanAsUnknown() {
        XCTAssertEqual(openIDPreview().sharingReview().request.responseProtection, SharingResponseProtection.none)
    }

    func testAnEncryptedOpenIDResponseReportsTheJWEAlgorithmsAndVerifierKey() {
        let review = openIDPreview(
            responseEncryption: .required(
                ResponseEncryptionDetails(
                    keyManagementAlgorithm: "ECDH-ES",
                    contentEncryptionAlgorithm: "A128GCM",
                    verifierKeyID: "verifier-key-1",
                    verifierKeyThumbprint: "thumb-1"
                )
            )
        ).sharingReview()

        XCTAssertEqual(
            review.request.responseProtection,
            .encrypted(
                mechanism: .jwe,
                keyManagementAlgorithm: "ECDH-ES",
                contentEncryptionAlgorithm: "A128GCM",
                verifierKeyID: "verifier-key-1",
                verifierKeyThumbprint: "thumb-1"
            )
        )
        XCTAssertEqual(SharingEncryptionMechanism.jwe.displayName, "JWE encrypted response")
    }

    func testAnnexCReportsTheHPKESuiteISO180137MandatesRatherThanAskingWhetherEncryptionWasRequested() {
        let review = annexCPreview().sharingReview()

        XCTAssertEqual(
            review.request.responseProtection,
            .encrypted(
                mechanism: .annexCHPKE,
                keyManagementAlgorithm: "DHKEM(P-256, HKDF-SHA256)",
                contentEncryptionAlgorithm: "AES-128-GCM"
            )
        )
        XCTAssertEqual(SharingEncryptionMechanism.annexCHPKE.displayName, "ISO 18013-7 Annex C HPKE")
        XCTAssertNotEqual(review.request.responseProtection, SharingResponseProtection.none)
    }

    // MARK: - Transaction data and technical details

    func testTransactionDataIsGroupedForReviewOnTheNormalOpenIDPath() {
        let review = openIDPreview(transactionData: [
            PresentationTransactionData(
                type: "urn:test:payment",
                displayName: "Payment authorization",
                credentialQueryIDs: ["pid"],
                supportedFields: ["amount"],
                rawJSON: #"{"type":"urn:test:payment","amount":"42.00 EUR"}"#,
                detailsJSON: #"{"amount":"42.00 EUR"}"#
            ),
        ]).sharingReview()

        XCTAssertEqual(
            review.request.transactionData.map(\.title),
            ["Payment authorization"],
            "The authorized transaction is headed by the profile's label, not by its transaction_data type"
        )
        let items = review.request.transactionData.flatMap(\.items)
        XCTAssertTrue(
            items.contains { $0.value == .text("42.00 EUR") },
            "Transaction authorization must be reviewable as claims, not as a raw JSON dump"
        )
        XCTAssertTrue(
            items.contains { $0.value == .text("urn:test:payment") },
            "The transaction type stays visible, since the label is chosen by the wallet profile rather than by the request"
        )
    }

    func testAnnexCHasNoTransactionDataAndNoOpenIDRequestParametersToShow() {
        let review = annexCPreview().sharingReview()

        XCTAssertTrue(review.request.transactionData.isEmpty)
        XCTAssertEqual(
            review.request.technicalDetails.map(\.label),
            ["Requested documents"],
            "A client ID, state or response URI here would be values the Annex C request never carried"
        )
        XCTAssertEqual(
            review.request.technicalDetails.first?.value,
            "org.iso.18013.5.1.mDL",
            "Requested document types come from the parsed request the raw request must later match"
        )
    }

    func testNormalOpenIDExposesTheProtocolParametersBehindTechnicalDetails() {
        let review = openIDPreview().sharingReview()

        XCTAssertEqual(
            review.request.technicalDetails.map(\.label),
            ["Client ID", "Response URI", "State", "Nonce"]
        )
        XCTAssertEqual(review.request.technicalDetails.first { $0.label == "State" }?.value, "state-123")
        XCTAssertEqual(review.request.technicalDetails.first { $0.label == "Nonce" }?.value, "nonce-456")
    }

    func testARejectedRequestStillDescribesWhoAskedSoTheUserCanDecideWhetherToNotifyThem() {
        let request = PresentationRequestContext(
            clientID: "https://verifier.example",
            verifierMetadata: VerifierMetadata(
                display: MetadataDisplay(name: "Example Verifier", locale: "en", logoURI: nil, logoAltText: nil),
                clientURI: nil,
                policyURI: nil,
                termsOfServiceURI: nil
            ),
            requestAuthentication: .unauthenticated,
            responseEncryption: .notRequired
        ).sharingRequest()

        XCTAssertEqual(request.requester?.display?.name, "Example Verifier")
        XCTAssertEqual(request.technicalDetails.first { $0.label == "Client ID" }?.value, "https://verifier.example")
        XCTAssertTrue(request.transactionData.isEmpty)
        XCTAssertNil(request.readerTrust)
    }

    // MARK: - Credential requirements and disclosures

    func testAnnexCRequestsEveryListedDocumentSoEachOneIsItsOwnRequirement() {
        let review = annexCPreview(
            documentTypes: ["org.iso.18013.5.1.mDL", "eu.europa.ec.eudi.pid.1"],
            credentialOptions: [
                option(queryID: "annexC.0", credentialID: "cred-mdl"),
                option(queryID: "annexC.0", credentialID: "cred-mdl-2"),
                option(queryID: "annexC.1", credentialID: "cred-pid"),
            ]
        ).sharingReview()

        XCTAssertEqual(
            review.credentialRequirements.map(\.options),
            [[["annexC.0"]], [["annexC.1"]]],
            "Annex C offers no alternative request sets, so both documents must be satisfied rather than either"
        )

        let opening = review.defaultCredentialSelection()
        XCTAssertEqual(Set(opening.map(\.queryID)), ["annexC.0", "annexC.1"])
        XCTAssertTrue(review.hasCompleteCredentialSelection(opening))
        XCTAssertFalse(
            review.hasCompleteCredentialSelection(opening.filter { $0.queryID == "annexC.0" }),
            "Share must stay unavailable while a requested document has no credential chosen"
        )
    }

    func testAnnexCDisclosuresAreReviewedThroughTheNormalRequestedDisclosureVocabulary() {
        let review = annexCPreview(credentialOptions: [
            option(
                queryID: "annexC.0",
                credentialID: "cred-mdl",
                disclosures: [
                    PresentationDisclosure(
                        path: "org.iso.18013.5.1.family_name",
                        name: "family_name",
                        valueJSON: "\"Doe\"",
                        displayValue: "Doe",
                        selectivelyDisclosable: true,
                        required: true,
                        selectable: false
                    ),
                ]
            ),
        ]).sharingReview()

        let details = CredentialDisplayNormalizer.details(for: review.credentialOptions[0])
        let requested = details.groups.first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }

        XCTAssertNotNil(
            requested,
            "Annex C elements must land in the same requested-disclosures group as any other request"
        )
        XCTAssertEqual(requested?.items.map(\.label), ["Family name"])
        XCTAssertFalse(
            review.credentialOptions[0].disclosures[0].selectable,
            "An Annex C element is requested outright, so presenting it as optional would misdescribe what sharing does"
        )
    }

    func testDeselectingACredentialDropsTheDisclosuresApprovedForIt() {
        let review = openIDPreview().sharingReview()
        let credential = review.credentialOptions[0].selection
        let disclosure = PresentationDisclosureSelection(
            queryID: credential.queryID,
            credentialID: credential.credentialID,
            path: "$.given_name"
        )

        let withDisclosure = review.toggling(
            disclosure: disclosure,
            in: SharingSelection(credentials: [credential])
        )
        XCTAssertEqual(withDisclosure.disclosures, [disclosure])

        let deselected = review.toggling(credential: credential, in: withDisclosure)
        XCTAssertEqual(deselected.credentials, [])
        XCTAssertEqual(
            deselected.disclosures,
            [],
            "A disclosure approved for one credential must not travel with the request once that credential is gone"
        )
    }

    // MARK: - Action availability

    func testShareStaysUnavailableUntilTheRequestIsSatisfied() {
        let review = openIDPreview().sharingReview()

        XCTAssertFalse(
            review.hasCompleteCredentialSelection([]),
            "Sharing nothing is not a way of answering a request"
        )
        XCTAssertTrue(review.hasCompleteCredentialSelection(review.defaultCredentialSelection()))
    }

    // MARK: - Fixtures

    private func openIDPreview(
        verifierMetadata: VerifierMetadata? = VerifierMetadata(
            display: MetadataDisplay(name: "Example Verifier", locale: "en", logoURI: nil, logoAltText: nil),
            clientURI: nil,
            policyURI: nil,
            termsOfServiceURI: nil
        ),
        responseEncryption: PresentationResponseEncryption = .notRequired,
        transactionData: [PresentationTransactionData] = []
    ) -> PresentationPreview {
        PresentationPreview(
            previewHandle: PresentationPreviewHandle(value: "preview"),
            request: PresentationRequestInfo(
                clientID: "https://verifier.example",
                verifierMetadata: verifierMetadata,
                requestAuthentication: .unauthenticated,
                responseURI: URL(string: "https://verifier.example/response"),
                state: "state-123",
                nonce: "nonce-456",
                responseEncryption: responseEncryption,
                transactionData: transactionData
            ),
            credentialOptions: [option(queryID: "pid", credentialID: "cred-1")],
            credentialRequirements: [PresentationCredentialRequirement(options: [["pid"]])]
        )
    }

    private func annexCPreview(
        verifiedOrigin: String = "https://reader.example",
        readerTrust: ReaderTrust = .pendingRawRequest,
        documentTypes: [String] = ["org.iso.18013.5.1.mDL"],
        credentialOptions: [PresentationCredentialOption]? = nil
    ) -> AnnexCPresentationPreview {
        AnnexCPresentationPreview(
            requestID: "request-1",
            verifiedOrigin: verifiedOrigin,
            parsedRequest: AnnexCParsedRequest(
                documents: documentTypes.map { documentType in
                    AnnexCDocumentRequest(
                        documentType: documentType,
                        namespaces: ["org.iso.18013.5.1": ["family_name"]]
                    )
                }
            ),
            credentialOptions: credentialOptions ?? [option(queryID: "annexC.0", credentialID: "cred-mdl")],
            readerTrust: readerTrust
        )
    }

    private func option(
        queryID: String,
        credentialID: String,
        disclosures: [PresentationDisclosure] = [
            PresentationDisclosure(
                path: "$.given_name",
                name: "given_name",
                valueJSON: "\"Ada\"",
                displayValue: "Ada",
                selectivelyDisclosable: true
            ),
        ]
    ) -> PresentationCredentialOption {
        PresentationCredentialOption(
            queryID: queryID,
            credentialID: credentialID,
            format: "mso_mdoc",
            issuer: "https://issuer.example",
            subject: "did:example:holder",
            label: "Mobile Driving Licence",
            credentialDataJSON: #"{"org.iso.18013.5.1":{"family_name":"Doe","given_name":"Ada"}}"#,
            disclosures: disclosures
        )
    }
}
