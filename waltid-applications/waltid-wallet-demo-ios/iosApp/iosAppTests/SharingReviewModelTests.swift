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

    // MARK: - Verifier identity

    func testNormalOpenIDReviewIsHeadedByVerifierMetadataAndNotByAVerifiedOrigin() {
        let review = openIDPreview(
            verifierMetadata: VerifierMetadata(
                display: MetadataDisplay(name: "Example Verifier", locale: "en", logoURI: nil, logoAltText: nil),
                clientURI: "https://verifier.example",
                policyURI: "https://verifier.example/privacy",
                termsOfServiceURI: nil
            )
        ).sharingReview()

        let verifier = try? XCTUnwrap(review.request.verifier)
        XCTAssertEqual(verifier?.display?.name, "Example Verifier")
        XCTAssertEqual(
            verifier?.verifiedOrigin,
            nil,
            "A plain OpenID4VP request has no platform-verified origin, and claiming one would present a self-asserted client ID as authenticated"
        )
        XCTAssertEqual(
            verifier?.details.map(\.label),
            ["Client URI", "Privacy policy", "Terms of service"]
        )
        XCTAssertEqual(verifier?.details.first { $0.label == "Privacy policy" }?.linkURI, "https://verifier.example/privacy")
    }

    func testARequestWithoutVerifierMetadataGetsNoVerifierIsland() {
        let review = openIDPreview(verifierMetadata: nil).sharingReview()

        XCTAssertNil(
            review.request.verifier,
            "An empty Verifier island would read as a named Verifier whose name happens to be missing"
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

        XCTAssertNil(review.request.verifier)
    }

    func testAnnexCReviewIsHeadedByTheVerifiedOriginTheWalletWillBindTheResponseTo() {
        let review = annexCPreview(verifiedOrigin: "https://reader.example").sharingReview()
        let verifier = try? XCTUnwrap(review.request.verifier)

        XCTAssertEqual(verifier?.fallbackName, "https://reader.example")
        XCTAssertEqual(
            verifier?.verifiedOrigin,
            "https://reader.example",
            "The origin is the only authenticated fact about an Annex C caller, so it must stay marked as verified"
        )
        XCTAssertNil(
            verifier?.display,
            "Annex C carries no verifier display metadata, and inventing one would name a caller the request never named"
        )
    }

    func testTheReviewDoesNotReinterpretTheOriginTheKMPCanonicalizerReturned() {
        let review = annexCPreview(verifiedOrigin: "https://Reader.Example:443/").sharingReview()

        XCTAssertEqual(
            review.request.verifier?.verifiedOrigin,
            "https://Reader.Example:443/",
            "Canonicalization belongs to the shared KMP layer; normalizing again here could disagree with what the verifier bound its request to"
        )
    }

    func testAnOriginThatHeadsTheVerifierIslandIsCaptionedAsVerifiedAndNotRepeated() {
        let verifier = annexCPreview(verifiedOrigin: "https://reader.example").sharingReview().request.verifier

        XCTAssertEqual(verifier?.identityName, "https://reader.example")
        XCTAssertEqual(
            verifier?.identityNameCaption,
            SharingVerifier.verifiedOriginLabel,
            "An uncaptioned heading reads as one more self-asserted Verifier claim, and the origin is the only authenticated one"
        )
        XCTAssertEqual(
            verifier?.detailRows.filter { $0.value?.isEmpty == false }.map(\.label),
            [],
            "The origin already heads the island, so a labelled row repeating it would read as a second, independent Verifier fact"
        )
    }

    func testAnOriginBesideSelfAssertedMetadataIsALabelledRowRatherThanACaption() {
        let verifier = SharingVerifier(
            display: MetadataDisplay(name: "Example Verifier", locale: "en", logoURI: nil, logoAltText: nil),
            fallbackName: "https://verifier.example",
            verifiedOrigin: "https://verifier.example",
            details: [SharingDetail(label: "Privacy policy", value: "https://verifier.example/privacy")]
        )

        XCTAssertEqual(verifier.identityName, "Example Verifier")
        XCTAssertNil(
            verifier.identityNameCaption,
            "The heading here is self-asserted metadata, and captioning it as verified would present it as authenticated"
        )
        XCTAssertEqual(
            verifier.detailRows.map { [$0.label, $0.value ?? ""] },
            [
                [SharingVerifier.verifiedOriginLabel, "https://verifier.example"],
                ["Privacy policy", "https://verifier.example/privacy"],
            ],
            "The origin stays visible under its own label: a review showing only the name a request asked to be called would hide the one verified fact"
        )
    }

    func testVerifiedNativeAppOriginUsesAppCopy() {
        let verifier = SharingVerifier(
            fallbackName: "android:apk-key-hash:example",
            verifiedOrigin: "android:apk-key-hash:example"
        )

        XCTAssertEqual(verifier.identityNameCaption, "Verified app")
    }

    func testUnknownVerifiedOriginSchemeUsesNeutralCopy() {
        XCTAssertEqual(
            SharingVerifier.verifiedOriginCaption(for: "custom:origin"),
            "Verified origin"
        )
    }

    func testNamedVerifierStartsCompactWhileVerifiedOriginDetailsStayVisible() throws {
        let named = SharingReviewModel(
            request: SharingRequest(verifier: SharingVerifier(fallbackName: "Example Verifier")),
            credentialOptions: []
        )
        let verified = SharingReviewModel(
            request: SharingRequest(
                verifier: SharingVerifier(
                    fallbackName: "Example Verifier",
                    verifiedOrigin: "https://verifier.example"
                )
            ),
            credentialOptions: []
        )

        XCTAssertFalse(try XCTUnwrap(named.reviewIslands().first).initiallyExpanded)
        XCTAssertTrue(try XCTUnwrap(verified.reviewIslands().first).initiallyExpanded)
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

    func testReviewIslandsPutHumanDecisionsBeforeProtocolDetails() {
        let review = openIDPreview(
            responseEncryption: .required(
                ResponseEncryptionDetails(
                    keyManagementAlgorithm: "ECDH-ES",
                    contentEncryptionAlgorithm: "A256GCM",
                    verifierKeyID: "verifier-key-1",
                    verifierKeyThumbprint: "thumbprint-1"
                )
            ),
            transactionData: [
                PresentationTransactionData(
                    type: "urn:test:payment",
                    displayName: "Payment authorization",
                    credentialQueryIDs: ["pid"],
                    supportedFields: ["amount"],
                    rawJSON: #"{"type":"urn:test:payment","amount":"42.00 EUR"}"#,
                    detailsJSON: #"{"amount":"42.00 EUR"}"#
                ),
            ]
        ).sharingReview()

        let islands = review.reviewIslands(context: .selectedForSharing)
        XCTAssertEqual(
            islands.map(\.kind),
            [.verifier, .credential, .information, .purposeAndTransaction]
        )
        XCTAssertEqual(islands.first?.title, "Example Verifier")
        XCTAssertEqual(islands.first?.summaryValues.first?.value, "Protected response")

        let normalCopy = islands.flatMap { island in
            [island.title, island.subtitle].compactMap { $0 }
                + island.summaryValues.compactMap(\.value)
                + island.expandedValues.compactMap(\.value)
        }
        XCTAssertFalse(normalCopy.contains("ECDH-ES"))
        XCTAssertFalse(normalCopy.contains("A256GCM"))
        XCTAssertFalse(normalCopy.contains("mso_mdoc"))

        let technicalCopy = islands.flatMap(\.technicalSections).flatMap(\.values).compactMap(\.value)
        XCTAssertTrue(technicalCopy.contains("ECDH-ES"))
        XCTAssertTrue(technicalCopy.contains("A256GCM"))
        XCTAssertTrue(technicalCopy.contains("mso_mdoc"))
    }

    func testDigitalCredentialsEncryptionUsesUserFacingOpenID4VPWording() {
        let review = SharingReviewModel(
            request: SharingRequest(
                verifier: SharingVerifier(fallbackName: "Example Verifier"),
                responseProtection: .encrypted(mechanism: .dcAPIJWT)
            ),
            credentialOptions: []
        )

        let values = review.reviewIslands().flatMap(\.technicalSections).flatMap(\.values)
        XCTAssertTrue(values.contains { $0.value == "OpenID4VP encrypted response" })
        XCTAssertFalse(values.contains { $0.value == "dc_api.jwt" })
    }

    func testConfiguredCredentialLabelIsPreservedForTheIslandSummary() throws {
        let credential = PresentationCredentialOption(
            queryID: "mdl",
            credentialID: "credential-1",
            format: "mso_mdoc",
            issuer: "Example Issuer",
            subject: nil,
            label: "mso_mdoc",
            credentialDataJSON: #"{"docType":"org.iso.18013.5.1.mDL"}"#,
            disclosures: []
        )
        let review = SharingReviewModel(
            request: SharingRequest(verifier: nil),
            credentialOptions: [credential]
        )

        let island = try XCTUnwrap(review.reviewIslands().first { $0.kind == .credential })
        XCTAssertEqual(island.title, "mso_mdoc")
        XCTAssertEqual(island.subtitle, "Example Issuer")
        XCTAssertTrue(island.expandedValues.isEmpty)
        XCTAssertTrue(
            island.technicalSections.flatMap(\.values).contains { $0.label == "Format" && $0.value == "mso_mdoc" }
        )
    }

    func testConfiguredCredentialLabelIsPreservedVerbatim() throws {
        let configuredLabel = "  mso_mdoc  "
        let credential = PresentationCredentialOption(
            queryID: "mdl",
            credentialID: "credential-1",
            format: "mso_mdoc",
            issuer: "Example Issuer",
            subject: nil,
            label: configuredLabel,
            credentialDataJSON: #"{"docType":"org.iso.18013.5.1.mDL"}"#,
            disclosures: []
        )

        let review = SharingReviewModel(
            request: SharingRequest(verifier: nil),
            credentialOptions: [credential]
        )
        let island = try XCTUnwrap(review.reviewIslands().first { $0.kind == .credential })

        XCTAssertEqual(island.title, configuredLabel)
    }

    func testSeveralCredentialOptionsUseOneNeutralIslandHeading() throws {
        let credentials = ["Personal ID", "Travel ID"].enumerated().map { index, label in
            PresentationCredentialOption(
                queryID: "pid",
                credentialID: "credential-\(index)",
                format: "vc+sd-jwt",
                issuer: "Example Issuer",
                subject: nil,
                label: label,
                credentialDataJSON: "{}",
                disclosures: []
            )
        }
        let review = SharingReviewModel(
            request: SharingRequest(verifier: nil),
            credentialOptions: credentials
        )

        let island = try XCTUnwrap(review.reviewIslands().first { $0.kind == .credential })

        XCTAssertEqual(island.title, "Choose credentials")
        XCTAssertEqual(island.subtitle, "2 credentials available")
        XCTAssertEqual(island.expandedValues.map(\.label), ["Personal ID", "Travel ID"])
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

    func testTransactionDisplayNameIsNotRewrittenWhenItMatchesTheType() throws {
        let configuredName = "org.waltid.transaction-data.payment_authorization"
        let groups = CredentialDisplayNormalizer.transactionDataGroups(for: [
            PresentationTransactionData(
                type: configuredName,
                displayName: configuredName,
                credentialQueryIDs: ["pid"],
                supportedFields: [],
                rawJSON: "{}",
                detailsJSON: "{}"
            ),
        ])
        let group = try XCTUnwrap(groups.first)

        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(group.title, configuredName)
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

        XCTAssertEqual(request.verifier?.display?.name, "Example Verifier")
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
