import Foundation
import WalletDemoSharingUI
import XCTest
@testable import WalletSDK
@testable import iosApp

/// Shared, non-personal input also consumed by Compose Android and Compose iOS.
struct WalletVisualFixtures {
    private let root: [String: Any]
    private let resourceDirectory: URL

    init() throws {
        let applications = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        resourceDirectory = applications.appendingPathComponent("waltid-wallet-demo-test-fixtures/resources/files")
        let url = resourceDirectory.appendingPathComponent("wallet-visual-data.json")
        root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        XCTAssertEqual(root["schemaVersion"] as? Int, 1)
    }

    @MainActor
    func keySetupOptions() throws -> [WalletIdentityScreenModel.SetupOption] {
        try array(object(root, "keySetup"), "options").map { item in
            func choice(_ name: String) throws -> WalletIdentityScreenModel.Selection {
                let value = try object(item, name)
                return .init(id: try text(value, "id"), title: try text(value, "title"), detail: try text(value, "detail"))
            }
            return .init(recovery: try choice("recovery"), storage: try choice("storage"), approval: try choice("approval"),
                restoring: false, perform: {})
        }
    }

    func credentialDetails() throws -> CredentialDetails {
        CredentialDisplayNormalizer.details(for: try credential())
    }

    func localizedCredentialDetails() throws -> CredentialDetails {
        let data = try Data(contentsOf: resourceDirectory.appendingPathComponent("credential-information.json"))
        let contract = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let stored = try credential()
        let value = Credential(id: stored.id, format: stored.format, issuer: stored.issuer, subject: nil,
            label: stored.label, addedAt: stored.addedAt,
            credentialDataJSON: try json(object(contract, "credentialData")), metadataJSON: try json(object(contract, "metadata")))
        return CredentialDisplayNormalizer.details(for: value, preferredLocales: try XCTUnwrap(contract["preferredLocales"] as? [String]))
    }

    func credential() throws -> Credential {
        let value = try object(root, "credential")
        let issuer = try object(root, "issuer")
        let metadata: [String: Any] = [
            "issuerDisplay": [["name": try text(issuer, "name"), "locale": "en"]],
            "credentialDisplay": [["name": try text(value, "title"), "locale": "en"]],
        ]
        return Credential(
            id: try text(value, "id"), format: try text(value, "format"),
            issuer: try text(issuer, "identifier"), subject: nil, label: try text(value, "title"),
            addedAt: ISO8601DateFormatter().date(from: try text(value, "addedAt")),
            credentialDataJSON: try json(object(value, "data")), metadataJSON: try json(metadata)
        )
    }

    func credentialImages() throws -> [ClaimItem] {
        let value = try object(root, "credential")
        return try [("portraitFile", "portrait", "Portrait", "image/jpeg"),
                    ("signatureFile", "signature_usual_mark", "Signature", "image/png")].map { file, path, label, mime in
            let bytes = try Data(contentsOf: resourceDirectory.appendingPathComponent(text(value, file)))
            return ClaimItem(path: .topLevel(path), label: label,
                value: .image(encoded: "data:\(mime);base64,\(bytes.base64EncodedString())", data: bytes,
                              mimeType: mime, byteCount: bytes.count), rawValue: nil)
        }
    }

    func nearbyQrPayload() throws -> String { try text(object(root, "nearby"), "qrPayload") }
    func partialResultStatus() throws -> String { try text(object(root, "batchOutcome"), "status") }
    func deferredCredential() throws -> DeferredCredential {
        let value = try object(root, "batchOutcome")
        return DeferredCredential(id: try text(value, "pendingId"),
            credentialConfigurationID: try text(value, "pendingConfigurationId"), intervalSeconds: 5,
            status: .awaitingIssuer, displayMetadataJSON: try json(object(value, "pendingMetadata")))
    }

    func offer() throws -> IssuanceOfferPreview {
        let value = try object(root, "offer")
        let issuer = try object(root, "issuer")
        return IssuanceOfferPreview(
            grant: .preAuthorizedCode,
            issuer: .init(identifier: try text(issuer, "identifier"), name: try text(issuer, "name"),
                          locale: "en", logoURI: nil, logoAltText: nil, metadataProvenance: .unsigned),
            credentials: try array(value, "credentials").map { item in
                let claims = try array(item, "claims").map { claim -> [String: Any] in
                    ["path": try XCTUnwrap(claim["path"] as? [String]),
                     "mandatory": try XCTUnwrap(claim["mandatory"] as? Bool),
                     "display": [["name": try text(claim, "label"), "locale": "en"]]]
                }
                let metadata = try JSONSerialization.data(withJSONObject: ["credentialClaims": claims])
                return .init(configurationID: try text(item, "configurationId"), format: try text(item, "format"),
                      name: try text(item, "title"), descriptionText: nil, logoURI: nil,
                      backgroundColor: try text(item, "backgroundColor"), textColor: "#FFFFFF",
                      metadataJSON: String(data: metadata, encoding: .utf8))
            },
            transactionCode: nil, batchSize: try XCTUnwrap(value["batchSize"] as? Int)
        )
    }

    func copies() throws -> [String: Int] {
        Dictionary(uniqueKeysWithValues: try array(object(root, "offer"), "credentials").map {
            (try text($0, "configurationId"), try XCTUnwrap($0["copies"] as? Int))
        })
    }

    func sharingReview(payment: Bool = false) throws -> SharingReviewModel {
        let value = try object(root, "sharing")
        let origin = try text(value, "origin")
        let all = try array(value, "credentials")
        let options: [PresentationCredentialOption] = try (payment ? all : Array(all.prefix(1))).map { item in
            PresentationCredentialOption(queryID: try text(item, "queryId"), credentialID: try text(item, "credentialId"),
                format: try text(item, "format"), issuer: try text(item, "issuer"), subject: nil, label: try text(item, "title"),
                credentialDataJSON: "{}", disclosures: try array(item, "disclosures").map { claim in
                    let value = try text(claim, "value")
                    let encoded = try JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed])
                    return PresentationDisclosure(path: try text(claim, "path"), name: try text(claim, "label"),
                        valueJSON: try XCTUnwrap(String(data: encoded, encoding: .utf8)), displayValue: value,
                        selectivelyDisclosable: false, required: false, selectable: false)
                })
        }
        return SharingReviewModel(request: SharingRequest(
            requester: SharingRequester(fallbackName: origin, verifiedOrigin: origin),
            readerTrust: payment ? nil : .pendingVerification,
            responseProtection: .encrypted(mechanism: payment ? .dcAPIJWT : .annexCHPKE),
            transactionData: payment ? [ClaimGroup(id: "transaction:0", title: "Payment", items: [], transactionType: "urn:eudi:sca:payment:1")] : []),
            credentialOptions: options,
            credentialRequirements: options.map { .init(options: [[$0.queryID]]) })
    }

    func payment() throws -> PaymentConsent {
        let value = try object(root, "payment")
        let placements: [String: PaymentConsentFieldPlacement] = [
            "Prominent": .prominent, "Main": .main, "Details": .details, "Omitted": .omitted,
        ]
        return PaymentConsent(
            revision: try text(value, "revision"), locale: try text(value, "locale"),
            title: try text(value, "title"), securityHint: nil,
            affirmativeAction: try text(value, "affirmativeAction"), denialAction: try text(value, "denialAction"),
            requiresUnsignedRequestWarning: true,
            fields: try array(value, "fields").map { field in
                PaymentConsentField(label: try text(field, "name"), description: nil, value: try text(field, "value"),
                                    placement: try XCTUnwrap(placements[text(field, "placement")]))
            }
        )
    }

    private func object(_ parent: [String: Any], _ key: String) throws -> [String: Any] {
        try XCTUnwrap(parent[key] as? [String: Any], "Missing fixture object: \(key)")
    }
    private func array(_ parent: [String: Any], _ key: String) throws -> [[String: Any]] {
        try XCTUnwrap(parent[key] as? [[String: Any]], "Missing fixture array: \(key)")
    }
    private func text(_ parent: [String: Any], _ key: String) throws -> String {
        try XCTUnwrap(parent[key] as? String, "Missing fixture text: \(key)")
    }
    private func json(_ value: [String: Any]) throws -> String {
        try XCTUnwrap(String(data: JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]), encoding: .utf8))
    }
}
