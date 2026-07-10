import XCTest
@testable import iosApp
import WalletSDK

final class CredentialDisplayNormalizerTests: XCTestCase {

    func testParsesCredentialJsonIntoReadableGroups() {
        let imageBytes = Self.tinyPngBytes
        let credential = Credential(
            id: "cred-1",
            format: "vc+sd-jwt",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: nil,
            credentialDataJSON: """
            {
              "given_name": "Ada",
              "resident_address": {
                "street_address": "Main Street 1"
              },
              "portrait": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p94AAAAASUVORK5CYII="
            }
            """
        )

        let details = CredentialDisplayNormalizer.details(for: credential)

        XCTAssertTrue(details.groups.contains { $0.title == "Personal details" })
        XCTAssertTrue(details.groups.contains(where: { group in
            group.items.contains(where: { $0.label == "Given name" && $0.value == .text("Ada") })
        }))
        XCTAssertTrue(details.groups.contains(where: { group in
            group.items.contains(where: { $0.label == "Resident address" })
        }))
        XCTAssertTrue(details.groups.contains(where: { group in
            group.items.contains(where: { item in
                guard case .image(_, let data, let mimeType, let byteCount) = item.value else {
                    return false
                }
                return item.label == "Portrait" &&
                    data == imageBytes &&
                    mimeType == "image/png" &&
                    byteCount == imageBytes.count
            })
        }))
    }

    func testNormalizesParameterizedDataURIImageMimeType() {
        let credential = Credential(
            id: "cred-1",
            format: "vc+sd-jwt",
            issuer: nil,
            subject: nil,
            label: nil,
            addedAt: nil,
            credentialDataJSON: #"{"portrait":"DATA:image/PNG;charset=utf-8;base64,  \#(Self.tinyPngBase64)  "}"#
        )

        let details = CredentialDisplayNormalizer.details(for: credential)
        let portrait = details.groups.flatMap(\.items).first { $0.path.id == "portrait" }

        guard case .image(_, let data, let mimeType, let byteCount) = portrait?.value else {
            return XCTFail("Expected data URI portrait to render as an image")
        }

        XCTAssertEqual(data, Self.tinyPngBytes)
        XCTAssertEqual(mimeType, "image/png")
        XCTAssertEqual(byteCount, Self.tinyPngBytes.count)
    }

    func testClassifiesPortraitByteArrayDataAsImage() {
        let signedBytes = Self.tinyPngBytes.map { byte in
            byte > 127 ? Int(byte) - 256 : Int(byte)
        }
        let credential = Credential(
            id: "cred-1",
            format: "mso_mdoc",
            issuer: nil,
            subject: nil,
            label: nil,
            addedAt: nil,
            credentialDataJSON: #"{"portrait":\#(signedBytes)}"#
        )

        let details = CredentialDisplayNormalizer.details(for: credential)

        XCTAssertTrue(details.groups.contains(where: { group in
            group.items.contains(where: { item in
                guard case .image(_, let data, let mimeType, let byteCount) = item.value else {
                    return false
                }
                return item.label == "Portrait" &&
                    data == Self.tinyPngBytes &&
                    mimeType == "image/png" &&
                    byteCount == Self.tinyPngBytes.count
            })
        }))
    }

    func testClassifiesNestedPortraitByteArrayDataAsImageAndCardPortrait() {
        let signedBytes = Self.tinyPngBytes.map { byte in
            byte > 127 ? Int(byte) - 256 : Int(byte)
        }
        let credential = Credential(
            id: "cred-1",
            format: "mso_mdoc",
            issuer: nil,
            subject: nil,
            label: nil,
            addedAt: nil,
            credentialDataJSON: #"{"portrait":{"elementValue":\#(signedBytes)}}"#
        )

        let details = CredentialDisplayNormalizer.details(for: credential)

        XCTAssertTrue(details.groups.contains(where: { group in
            group.items.contains(where: { item in
                guard item.label == "Portrait",
                      case .object(let children) = item.value,
                      let nested = children.first(where: { $0.path.id == "portrait.elementValue" }),
                      case .image(_, let data, let mimeType, let byteCount) = nested.value else {
                    return false
                }
                return data == Self.tinyPngBytes &&
                    mimeType == "image/png" &&
                    byteCount == Self.tinyPngBytes.count
            })
        }))
        XCTAssertEqual(details.cardSummary.portraitData, Self.tinyPngBytes)
        XCTAssertEqual(details.cardSummary.portraitMimeType, "image/png")
    }

    func testClassifiesPresentationDisclosureByteArrayDataAsImage() {
        let signedBytes = Self.tinyPngBytes.map { byte in
            byte > 127 ? Int(byte) - 256 : Int(byte)
        }
        let option = PresentationCredentialOption(
            queryID: "pid",
            credentialID: "cred-1",
            format: "mso_mdoc",
            issuer: "Example Issuer",
            subject: nil,
            label: "PID",
            credentialDataJSON: nil,
            disclosures: [
                PresentationDisclosure(
                    path: "$.portrait",
                    name: "Portrait",
                    valueJSON: "\(signedBytes)",
                    displayValue: nil,
                    selectivelyDisclosable: true
                )
            ]
        )

        let details = CredentialDisplayNormalizer.details(for: option)
        let disclosure = details.groups
            .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
            .items
            .first

        guard case .image(_, let data, let mimeType, let byteCount) = disclosure?.value else {
            return XCTFail("Expected presentation disclosure to render as an image")
        }

        XCTAssertEqual(disclosure?.path.id, "disclosures[0].portrait")
        XCTAssertEqual(disclosure?.path.sourcePath, "$.portrait")
        XCTAssertEqual(data, Self.tinyPngBytes)
        XCTAssertEqual(mimeType, "image/png")
        XCTAssertEqual(byteCount, Self.tinyPngBytes.count)
        XCTAssertEqual(disclosure?.rawValue, "\(signedBytes)")
    }

    func testClassifiesDisclosureSemanticsFromSdkPathWhenNameIsMissing() {
        let signedBytes = Self.tinyPngBytes.map { byte in
            byte > 127 ? Int(byte) - 256 : Int(byte)
        }
        let option = PresentationCredentialOption(
            queryID: "pid",
            credentialID: "cred-1",
            format: "mso_mdoc",
            issuer: "Example Issuer",
            subject: nil,
            label: "PID",
            credentialDataJSON: nil,
            disclosures: [
                PresentationDisclosure(
                    path: "$.portrait",
                    name: nil,
                    valueJSON: "\(signedBytes)",
                    displayValue: nil,
                    selectivelyDisclosable: true
                )
            ]
        )

        let details = CredentialDisplayNormalizer.details(for: option)
        let disclosure = details.groups
            .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
            .items
            .first

        XCTAssertEqual(disclosure?.label, "Portrait")
        guard case .image(_, let data, let mimeType, _) = disclosure?.value else {
            return XCTFail("Expected disclosure path to identify portrait image data")
        }
        XCTAssertEqual(data, Self.tinyPngBytes)
        XCTAssertEqual(mimeType, "image/png")
    }

    func testBuildsReadableDisclosureLabelsFromSdkPath() {
        XCTAssertEqual(CredentialDisplayVocabulary.disclosureLabel(name: nil, path: "$.portrait"), "Portrait")
        XCTAssertEqual(CredentialDisplayVocabulary.disclosureLabel(name: nil, path: "$.given_name"), "Given name")
        XCTAssertEqual(CredentialDisplayVocabulary.disclosureLabel(name: nil, path: "$.credentialSubject['family.name']"), "Family name")
        XCTAssertEqual(CredentialDisplayVocabulary.disclosureLabel(name: nil, path: "$['credentialSubject']['resident.address']"), "Resident address")
        XCTAssertEqual(CredentialDisplayVocabulary.disclosureLabel(name: "Visible name", path: "$.given_name"), "Visible name")
        XCTAssertEqual(CredentialDisplayVocabulary.disclosureLabel(name: "   ", path: "$.given_name"), "Given name")
    }

    func testClassifiesHumanLabelledMdocClaimsForDetailsAndCards() {
        let signedBytes = Self.tinyPngBytes.map { byte in
            byte > 127 ? Int(byte) - 256 : Int(byte)
        }
        let credential = Credential(
            id: "cred-1",
            format: "mso_mdoc",
            issuer: nil,
            subject: nil,
            label: nil,
            addedAt: nil,
            credentialDataJSON: """
            {
              "Given name": "Ada",
              "Family name": "Lovelace",
              "Date of birth": "1815-12-10",
              "Place of birth": "London",
              "Valid to": 1781654400,
              "Portrait": {
                "elementValue": \(signedBytes)
              }
            }
            """
        )

        let details = CredentialDisplayNormalizer.details(for: credential)
        let summary = details.cardSummary

        XCTAssertTrue(details.groups.contains { $0.title == "Personal details" })
        XCTAssertTrue(details.groups.contains(where: { group in
            group.title == "Personal details" &&
                group.items.contains { $0.path.id == "Date of birth" && $0.value == .text("1815-12-10") } &&
                group.items.contains { $0.path.id == "Place of birth" && $0.value == .text("London") }
        }))
        XCTAssertTrue(details.groups.contains(where: { group in
            group.items.contains(where: { item in
                guard item.path.id == "Portrait",
                      case .object(let children) = item.value,
                      let nested = children.first(where: { $0.path.id == "Portrait.elementValue" }),
                      case .image(_, let data, let mimeType, _) = nested.value else {
                    return false
                }
                return data == Self.tinyPngBytes && mimeType == "image/png"
            })
        }))
        XCTAssertEqual(summary.holderName, "Ada Lovelace")
        XCTAssertEqual(summary.dateText, "2026-06-17")
        XCTAssertEqual(summary.validityText, "Expires 2026-06-17")
        XCTAssertEqual(summary.portraitData, Self.tinyPngBytes)
    }

    func testDecodesBase64Text() {
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: nil,
            subject: nil,
            label: nil,
            addedAt: nil,
            credentialDataJSON: #"{"encoded_note":"SGVsbG8gd2FsbGV0"}"#
        )

        let details = CredentialDisplayNormalizer.details(for: credential)

        XCTAssertTrue(details.groups.contains(where: { group in
            group.items.contains(where: { $0.label == "Encoded note" && $0.value == .decodedText("Hello wallet") })
        }))
    }

    func testBuildsCredentialCardSummaryFromReadableClaims() {
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: nil,
            credentialDataJSON: """
            {
              "given_name": "Ada",
              "family_name": "Lovelace",
              "exp": "2026-06-17",
              "portrait": "\(Self.tinyPngBase64)"
            }
            """
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.title, "Example Credential")
        XCTAssertEqual(summary.holderName, "Ada Lovelace")
        XCTAssertEqual(summary.dateText, "2026-06-17")
        XCTAssertEqual(summary.portraitData, Self.tinyPngBytes)
        XCTAssertEqual(summary.portraitMimeType, "image/png")
    }

    func testFormatsNumericTemporalClaimsAsReadableDatesForDetailsAndCards() {
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: nil,
            credentialDataJSON: """
            {
              "given_name": "Ada",
              "family_name": "Lovelace",
              "exp": 1781654400,
              "nbf": 1781568000000
            }
            """
        )

        let details = CredentialDisplayNormalizer.details(for: credential)
        let items = details.groups.flatMap(\.items)

        XCTAssertTrue(items.contains { $0.path.id == "exp" && $0.value == .text("2026-06-17") })
        XCTAssertTrue(items.contains { $0.path.id == "nbf" && $0.value == .text("2026-06-16") })
        XCTAssertEqual(details.cardSummary.dateText, "2026-06-17")
        XCTAssertEqual(details.cardSummary.validityText, "Expires 2026-06-17")
    }

    func testBuildsCredentialCardSummaryFromValidToClaim() {
        let addedAt = ISO8601DateFormatter().date(from: "2026-07-09T12:00:00Z")!
        let credential = Credential(
            id: "cred-1",
            format: "mso_mdoc",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: addedAt,
            credentialDataJSON: """
            {
              "given_name": "Ada",
              "valid_to": 1781654400
            }
            """
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.dateText, "2026-06-17")
    }

    func testCredentialCardSummaryLabelsAddedAtFallbackWhenNoExpiryClaimExists() {
        let addedAt = ISO8601DateFormatter().date(from: "2026-07-09T12:00:00Z")!
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: addedAt,
            credentialDataJSON: #"{"given_name":"Ada"}"#
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.dateText, "2026-07-09")
        XCTAssertEqual(summary.validityText, "Added 2026-07-09")
    }

    func testBuildsCredentialCardSummaryTypeFromReadableCredentialTypeClaim() {
        let credential = Credential(
            id: "cred-1",
            format: "vc+sd-jwt",
            issuer: "Example Issuer",
            subject: nil,
            label: "PID",
            addedAt: nil,
            credentialDataJSON: """
            {
              "vct": "https://issuer.example/credential-types/mobile-driving-licence",
              "given_name": "Ada"
            }
            """
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.credentialType, "Mobile driving licence")
    }

    func testBuildsCredentialCardSummaryTypeFromURLClaimWithoutQueryOrFragmentNoise() {
        let credential = Credential(
            id: "cred-1",
            format: "vc+sd-jwt",
            issuer: "Example Issuer",
            subject: nil,
            label: "PID",
            addedAt: nil,
            credentialDataJSON: """
            {
              "vct": "https://issuer.example/credential-types/mobile-driving-licence?version=1#current",
              "given_name": "Ada"
            }
            """
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.credentialType, "Mobile driving licence")
    }

    func testBuildsCredentialCardSummaryTypeFromVcTypeArray() {
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: nil,
            credentialDataJSON: """
            {
              "type": ["VerifiableCredential", "UniversityDegreeCredential"],
              "given_name": "Ada"
            }
            """
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.credentialType, "University degree credential")
    }

    func testBuildsCredentialCardSummaryTypeFromURNTypeArray() {
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: nil,
            credentialDataJSON: """
            {
              "type": ["VerifiableCredential", "urn:example:ExampleCredential"],
              "given_name": "Ada"
            }
            """
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.credentialType, "Example credential")
    }

    func testCredentialCardSummaryIgnoresNestedTypeClaims() {
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: nil,
            credentialDataJSON: """
            {
              "document": {
                "type": "metadata"
              },
              "given_name": "Ada"
            }
            """
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertNil(summary.credentialType)
    }

    func testDoesNotInferClaimRolesFromPunctuationInsideClaimNames() {
        let credential = Credential(
            id: "cred-1",
            format: "vc+sd-jwt",
            issuer: "Example Issuer",
            subject: nil,
            label: "PID",
            addedAt: nil,
            credentialDataJSON: """
            {
              "metadata.exp": 1781654400
            }
            """
        )

        let details = CredentialDisplayNormalizer.details(for: credential)
        let metadataExpiry = details.groups.flatMap(\.items).first { $0.path.id == "metadata.exp" }

        XCTAssertEqual(metadataExpiry?.value, .number("1781654400"))
        XCTAssertNil(details.cardSummary.dateText)
    }

    func testCredentialCardSummaryFallsBackToAddedAtWhenNoExpiryClaimExists() {
        let addedAt = ISO8601DateFormatter().date(from: "2026-07-09T12:00:00Z")!
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: addedAt,
            credentialDataJSON: #"{"given_name":"Ada"}"#
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.dateText, "2026-07-09")
    }

    func testCredentialCardSummaryFallsBackToSubjectWhenNameClaimsAreMissing() {
        let credential = Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: "did:example:holder",
            label: "Example Credential",
            addedAt: nil,
            credentialDataJSON: #"{"credential_role":"Member"}"#
        )

        let summary = CredentialDisplayNormalizer.details(for: credential).cardSummary

        XCTAssertEqual(summary.holderName, "did:example:holder")
    }

    func testBuildsHumanReadableVerifierNamesWithoutDumpingRawIdentifiers() {
        XCTAssertEqual(
            VerifierDisplayName.value(
                verifierName: "Example Verifier",
                clientID: "decentralized_identifier:did:jwk:abc",
                responseURI: nil
            ),
            "Example Verifier"
        )
        XCTAssertEqual(
            VerifierDisplayName.value(
                verifierName: nil,
                clientID: "decentralized_identifier:did:jwk:abc",
                responseURI: nil
            ),
            "DID verifier"
        )
        XCTAssertEqual(
            VerifierDisplayName.value(
                verifierName: nil,
                clientID: "did:jwk:legacy-abc",
                responseURI: nil
            ),
            "DID verifier"
        )
        XCTAssertEqual(
            VerifierDisplayName.value(
                verifierName: nil,
                clientID: "https://verifier.example:8443/client?x=1#fragment",
                responseURI: nil
            ),
            "verifier.example"
        )
        XCTAssertEqual(
            VerifierDisplayName.value(
                verifierName: nil,
                clientID: "HTTPS://verifier.example/client",
                responseURI: nil
            ),
            "verifier.example"
        )
        XCTAssertEqual(
            VerifierDisplayName.value(
                verifierName: nil,
                clientID: "redirect_uri:https://verifier.example/callback",
                responseURI: nil
            ),
            "verifier.example"
        )
        XCTAssertEqual(
            VerifierDisplayName.value(
                verifierName: nil,
                clientID: "x509_san_dns:verifier.example",
                responseURI: nil
            ),
            "verifier.example"
        )
    }

    @MainActor
    func testStartNewReceiveFlowClearsCompletedStateAndStatus() {
        let viewModel = WalletViewModel(walletID: "reset-receive-\(UUID().uuidString)")
        viewModel.offerUrl = "openid-credential-offer://example"
        viewModel.lastReceivedCredentialIDs = ["cred-1"]
        viewModel.receiveCompleted = true
        viewModel.isLoading = true
        viewModel.isError = true
        viewModel.statusMessage = "Received 1 credential(s)"

        let resetKeyBeforeNewFlow = viewModel.receiveNavigationResetKey
        viewModel.startNewReceiveFlow()

        XCTAssertEqual(viewModel.offerUrl, "")
        XCTAssertEqual(viewModel.lastReceivedCredentialIDs, [])
        XCTAssertFalse(viewModel.receiveCompleted)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.isError)
        XCTAssertEqual(viewModel.receiveNavigationResetKey, resetKeyBeforeNewFlow + 1)
        XCTAssertEqual(viewModel.statusMessage, "Wallet ready")
    }

    @MainActor
    func testReceiveFlowAvailabilityFollowsLoadingAndCompletionState() {
        let viewModel = WalletViewModel(walletID: "receive-availability-\(UUID().uuidString)")
        viewModel.isReady = true
        viewModel.isLoading = false

        XCTAssertTrue(viewModel.receiveUrlEntryEnabled)
        XCTAssertFalse(viewModel.receiveActionEnabled)

        viewModel.offerUrl = "openid-credential-offer://example"
        XCTAssertTrue(viewModel.receiveActionEnabled)

        viewModel.isLoading = true
        XCTAssertFalse(viewModel.receiveUrlEntryEnabled)
        XCTAssertFalse(viewModel.receiveActionEnabled)

        viewModel.isLoading = false
        viewModel.receiveCompleted = true
        XCTAssertFalse(viewModel.receiveUrlEntryEnabled)
        XCTAssertFalse(viewModel.receiveActionEnabled)

        viewModel.startNewReceiveFlow()
        XCTAssertTrue(viewModel.receiveUrlEntryEnabled)
        XCTAssertFalse(viewModel.receiveActionEnabled)
    }

    @MainActor
    func testReceiveCredentialRefreshesCredentialsAndLocksCompletedFlow() async throws {
        let viewModel = WalletViewModel(
            walletID: "receive-refresh-\(UUID().uuidString)",
            walletClient: MockWalletClient()
        )

        try await waitUntil { viewModel.isReady }
        XCTAssertTrue(viewModel.credentials.isEmpty)

        viewModel.offerUrl = "openid-credential-offer://mock"
        XCTAssertTrue(viewModel.receiveActionEnabled)

        viewModel.receiveCredential()

        try await waitUntil { viewModel.receiveCompleted }
        XCTAssertEqual(viewModel.credentials.map(\.id), ["cred-1"])
        XCTAssertEqual(viewModel.lastReceivedCredentialIDs, ["cred-1"])
        XCTAssertFalse(viewModel.receiveUrlEntryEnabled)
        XCTAssertFalse(viewModel.receiveActionEnabled)
        XCTAssertEqual(viewModel.statusMessage, "Received 1 credential(s)")
        XCTAssertFalse(viewModel.isError)
    }

    @MainActor
    func testReceiveCredentialDerivesNewCredentialsWhenClientReturnsNoIds() async throws {
        let existingCredential = Credential(
            id: "old-cred",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Existing Credential",
            addedAt: nil,
            credentialDataJSON: nil
        )
        let newCredential = Credential(
            id: "new-cred",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "New Credential",
            addedAt: nil,
            credentialDataJSON: nil
        )
        let viewModel = WalletViewModel(
            walletID: "receive-derived-\(UUID().uuidString)",
            walletClient: ReceiveFallbackWalletClient(
                initialCredentials: [existingCredential],
                credentialsAfterReceive: [existingCredential, newCredential],
                receiveCredentialIDs: []
            )
        )

        try await waitUntil { viewModel.isReady }
        XCTAssertEqual(viewModel.credentials.map(\.id), ["old-cred"])

        viewModel.offerUrl = "openid-credential-offer://mock"
        viewModel.receiveCredential()

        try await waitUntil { viewModel.receiveCompleted }
        XCTAssertEqual(viewModel.credentials.map(\.id), ["old-cred", "new-cred"])
        XCTAssertEqual(viewModel.lastReceivedCredentialIDs, ["new-cred"])
        XCTAssertEqual(viewModel.receivedCredentials.map(\.id), ["new-cred"])
        XCTAssertEqual(viewModel.statusMessage, "Received 1 credential(s)")
    }

    @MainActor
    func testReceiveCredentialDoesNotCompleteWhenNoDisplayableCredentialIsAvailable() async throws {
        let viewModel = WalletViewModel(
            walletID: "receive-missing-\(UUID().uuidString)",
            walletClient: ReceiveFallbackWalletClient(
                initialCredentials: [],
                credentialsAfterReceive: [],
                receiveCredentialIDs: ["missing-cred"]
            )
        )

        try await waitUntil { viewModel.isReady }
        viewModel.offerUrl = "openid-credential-offer://mock"
        viewModel.receiveCredential()

        try await waitUntil { viewModel.isError }
        XCTAssertFalse(viewModel.receiveCompleted)
        XCTAssertEqual(viewModel.lastReceivedCredentialIDs, [])
        XCTAssertTrue(viewModel.receiveUrlEntryEnabled)
        XCTAssertEqual(viewModel.statusMessage, "Receive failed: received credentials are not available locally")
    }

    @MainActor
    func testStartNewPresentationFlowClearsCompletedStateAndStatus() {
        let viewModel = WalletViewModel(walletID: "reset-present-\(UUID().uuidString)")
        viewModel.presentationRequestUrl = "openid4vp://example"
        viewModel.selectedPresentationCredentialIDs = ["cred-1"]
        viewModel.presentationCompleted = true
        viewModel.isLoading = true
        viewModel.isError = true
        viewModel.statusMessage = "Presentation sent"

        let resetKeyBeforeNewFlow = viewModel.presentationNavigationResetKey
        viewModel.startNewPresentationFlow()

        XCTAssertEqual(viewModel.presentationRequestUrl, "")
        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertEqual(viewModel.selectedPresentationCredentialIDs, [])
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.isError)
        XCTAssertEqual(viewModel.presentationNavigationResetKey, resetKeyBeforeNewFlow + 1)
        XCTAssertEqual(viewModel.statusMessage, "Wallet ready")
    }

    @MainActor
    func testPresentationFlowAvailabilityFollowsPreviewAndCompletionState() {
        let viewModel = WalletViewModel(walletID: "present-availability-\(UUID().uuidString)")
        viewModel.isReady = true
        viewModel.isLoading = false
        viewModel.credentials = [
            Credential(
                id: "cred-1",
                format: "jwt_vc_json",
                issuer: "Example Issuer",
                subject: nil,
                label: "Example Credential",
                addedAt: nil,
                credentialDataJSON: nil
            )
        ]

        XCTAssertTrue(viewModel.presentationUrlEntryEnabled)
        XCTAssertFalse(viewModel.presentationPreviewActionEnabled)

        viewModel.presentationRequestUrl = "openid4vp://example"
        XCTAssertTrue(viewModel.presentationPreviewActionEnabled)

        viewModel.presentationPreview = PresentationPreview(
            request: PresentationRequestInfo(
                clientID: "https://verifier.example",
                verifierName: "Example Verifier"
            ),
            credentialOptions: []
        )
        XCTAssertFalse(viewModel.presentationUrlEntryEnabled)
        XCTAssertFalse(viewModel.presentationPreviewActionEnabled)

        viewModel.presentationPreview = nil
        viewModel.presentationCompleted = true
        XCTAssertFalse(viewModel.presentationUrlEntryEnabled)
        XCTAssertFalse(viewModel.presentationPreviewActionEnabled)

        viewModel.startNewPresentationFlow()
        XCTAssertTrue(viewModel.presentationUrlEntryEnabled)
        XCTAssertFalse(viewModel.presentationPreviewActionEnabled)
    }

    @MainActor
    func testSubmitPresentationKeepsCompletedPreviewAvailableUntilNewFlow() async throws {
        let viewModel = WalletViewModel(
            walletID: "present-completed-preview-\(UUID().uuidString)",
            walletClient: MockWalletClient(storedCredentials: [
                Credential(
                    id: "cred-1",
                    format: "jwt_vc_json",
                    issuer: "Example Issuer",
                    subject: nil,
                    label: "Example Credential",
                    addedAt: nil,
                    credentialDataJSON: nil
                )
            ])
        )

        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }

        viewModel.submitPresentation()
        try await waitUntil { viewModel.presentationCompleted }

        XCTAssertNotNil(viewModel.presentationPreview)
        XCTAssertEqual(viewModel.selectedPresentationCredentialIDs, [])
        XCTAssertFalse(viewModel.presentationReviewEnabled)
        XCTAssertFalse(viewModel.presentationUrlEntryEnabled)
        XCTAssertFalse(viewModel.presentationPreviewActionEnabled)
        XCTAssertEqual(viewModel.statusMessage, "Presentation sent")
        XCTAssertEqual(viewModel.statusMessage(for: .present), "Presentation sent")
        XCTAssertEqual(viewModel.statusMessage(for: .credentials), "Wallet ready")
        XCTAssertEqual(viewModel.statusMessage(for: .receive), "Wallet ready")

        viewModel.startNewPresentationFlow()

        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertTrue(viewModel.presentationUrlEntryEnabled)
    }

    @MainActor
    func testCancelPresentationReviewClearsPreviewWithoutProtocolRejection() async throws {
        let viewModel = WalletViewModel(
            walletID: "present-cancel-preview-\(UUID().uuidString)",
            walletClient: MockWalletClient(storedCredentials: [
                Credential(
                    id: "cred-1",
                    format: "jwt_vc_json",
                    issuer: "Example Issuer",
                    subject: nil,
                    label: "Example Credential",
                    addedAt: nil,
                    credentialDataJSON: nil
                )
            ])
        )

        try await waitUntil { viewModel.isReady }
        viewModel.presentationRequestUrl = "openid4vp://mock"
        viewModel.previewPresentation()
        try await waitUntil { viewModel.presentationPreview != nil }

        let resetKeyBeforeCancel = viewModel.presentationNavigationResetKey
        viewModel.cancelPresentationReview()

        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertEqual(viewModel.selectedPresentationCredentialIDs, [])
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertEqual(viewModel.presentationNavigationResetKey, resetKeyBeforeCancel + 1)
        XCTAssertEqual(viewModel.statusMessage(for: .present), "Presentation review cancelled")
        XCTAssertEqual(viewModel.statusMessage(for: .credentials), "Wallet ready")
        XCTAssertEqual(viewModel.statusMessage(for: .receive), "Wallet ready")
    }

    @MainActor
    func testDeepLinksRouteToMatchingTabsAndResetFlowState() throws {
        let viewModel = WalletViewModel(walletID: "deeplink-\(UUID().uuidString)")
        viewModel.lastReceivedCredentialIDs = ["old-credential"]
        viewModel.receiveCompleted = true
        viewModel.presentationPreview = PresentationPreview(
            request: PresentationRequestInfo(
                clientID: "https://verifier.example",
                verifierName: "Example Verifier"
            ),
            credentialOptions: []
        )
        viewModel.selectedPresentationCredentialIDs = ["old-presentation"]
        viewModel.presentationCompleted = true
        viewModel.isReady = true
        viewModel.isLoading = true
        viewModel.isError = true
        viewModel.statusMessage = "Receive failed: old error"

        let offerUrl = try XCTUnwrap(URL(string: "openid-credential-offer://example"))
        viewModel.handleDeepLink(offerUrl)

        XCTAssertEqual(viewModel.selectedTab, .receive)
        XCTAssertEqual(viewModel.offerUrl, offerUrl.absoluteString)
        XCTAssertEqual(viewModel.receiveNavigationResetKey, 1)
        XCTAssertEqual(viewModel.presentationNavigationResetKey, 0)
        XCTAssertEqual(viewModel.lastReceivedCredentialIDs, [])
        XCTAssertFalse(viewModel.receiveCompleted)
        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertEqual(viewModel.selectedPresentationCredentialIDs, [])
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.isError)
        XCTAssertEqual(viewModel.statusMessage, "Wallet ready")

        viewModel.isLoading = true
        viewModel.isError = true
        viewModel.statusMessage = "Preview failed: old error"

        let presentationUrl = try XCTUnwrap(URL(string: "openid4vp://example"))
        viewModel.handleDeepLink(presentationUrl)

        XCTAssertEqual(viewModel.selectedTab, .present)
        XCTAssertEqual(viewModel.presentationRequestUrl, presentationUrl.absoluteString)
        XCTAssertEqual(viewModel.receiveNavigationResetKey, 1)
        XCTAssertEqual(viewModel.presentationNavigationResetKey, 1)
        XCTAssertNil(viewModel.presentationPreview)
        XCTAssertEqual(viewModel.selectedPresentationCredentialIDs, [])
        XCTAssertFalse(viewModel.presentationCompleted)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.isError)
        XCTAssertEqual(viewModel.statusMessage, "Wallet ready")

        viewModel.handleDeepLink(presentationUrl)

        XCTAssertEqual(viewModel.presentationNavigationResetKey, 2)
    }

    private static let tinyPngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p94AAAAASUVORK5CYII="
    private static let tinyPngBytes = Data(base64Encoded: tinyPngBase64)!

    @MainActor
    private func waitUntil(
        timeout: Duration = .seconds(2),
        condition: @escaping @MainActor () -> Bool
    ) async throws {
        let deadline = ContinuousClock.now + timeout
        while !condition() {
            if ContinuousClock.now >= deadline {
                XCTFail("Timed out waiting for condition")
                return
            }
            try await Task.sleep(for: .milliseconds(20))
        }
    }
}

private actor ReceiveFallbackWalletClient: WalletClient {
    private var storedCredentials: [Credential]
    private let credentialsAfterReceive: [Credential]
    private let receiveCredentialIDs: [String]

    init(
        initialCredentials: [Credential],
        credentialsAfterReceive: [Credential],
        receiveCredentialIDs: [String]
    ) {
        self.storedCredentials = initialCredentials
        self.credentialsAfterReceive = credentialsAfterReceive
        self.receiveCredentialIDs = receiveCredentialIDs
    }

    func bootstrap() async throws -> WalletBootstrapResult {
        WalletBootstrapResult(keyID: "mock-key-1", did: "did:key:mock")
    }

    func credentials() async throws -> [Credential] {
        storedCredentials
    }

    func receive(offer: URL) async throws -> [String] {
        storedCredentials = credentialsAfterReceive
        return receiveCredentialIDs
    }

    func present(request: URL, did: String?) async throws -> PresentationResult {
        PresentationResult(success: true, redirectTo: nil, verifierResponseJSON: nil)
    }

    func previewPresentation(request: URL) async throws -> PresentationPreview {
        PresentationPreview(
            request: PresentationRequestInfo(clientID: nil, verifierName: nil),
            credentialOptions: []
        )
    }

    func submitPresentation(request: URL, selectedCredentialIDs: [String], did: String?) async throws -> PresentationResult {
        PresentationResult(success: true, redirectTo: nil, verifierResponseJSON: nil)
    }
}
