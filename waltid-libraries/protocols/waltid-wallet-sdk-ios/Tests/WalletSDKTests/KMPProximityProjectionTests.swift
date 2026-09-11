#if canImport(WalletCore) && os(iOS)
import XCTest
@preconcurrency import WalletCore
@testable import WalletSDK

final class KMPProximityProjectionTests: XCTestCase {
    func testSubmissionProjectionPreservesEveryApprovedField() {
        let fields: Set<WalletCore.ProximityElementReference> = [
            .init(namespace: "org.iso.18013.5.1", elementIdentifier: "age_over_18"),
            .init(namespace: "org.iso.18013.5.1", elementIdentifier: "portrait"),
        ]
        for continueAfterResponse in [false, true] {
            let submission = WalletCore.ProximitySubmission(documents: [
                .init(requestIndex: 0, credentialId: "credential-1", disclosedElements: fields),
                .init(requestIndex: 1, credentialId: "credential-2", disclosedElements: fields),
            ], continueAfterResponse: continueAfterResponse)

            let projected = submission.toSwiftSubmission()

            XCTAssertEqual(projected.continueAfterResponse, continueAfterResponse)
            XCTAssertEqual(projected.documents.map(\.requestIndex), [0, 1])
            XCTAssertEqual(projected.documents.map(\.credentialID), ["credential-1", "credential-2"])
            for document in projected.documents {
                XCTAssertEqual(document.disclosedElements, [
                    .init(namespace: "org.iso.18013.5.1", elementIdentifier: "age_over_18"),
                    .init(namespace: "org.iso.18013.5.1", elementIdentifier: "portrait"),
                ])
            }
        }
    }

    func testReviewProjectionPreservesRequiredFieldsAndAllowsNone() throws {
        let portrait = WalletCore.ProximityElementReference(
            namespace: "org.iso.18013.5.1", elementIdentifier: "portrait"
        )
        let option = WalletCore.ProximityCredentialOption(
            credentialId: "credential-1", label: "Identity", issuer: nil,
            validUntil: KotlinInstant.companion.fromEpochSeconds(epochSeconds: 2_000_000_000, nanosecondAdjustment: Int32(0)),
            deviceAuthentication: .signature,
            requestedElements: [.init(namespace: portrait.namespace, elementIdentifier: portrait.elementIdentifier,
                                      intentToRetain: false, satisfiesRequestedElements: [])]
        )
        for requiredFields: Set<WalletCore.ProximityElementReference> in [[], [portrait]] {
            let review = WalletCore.ProximityDocumentReview(
                requestIndex: 0, docType: "org.iso.18013.5.1.mDL",
                credentialOptions: [option], requiredElements: requiredFields
            )

            let projected = try review.toSwiftReview()

            let expected: Set<WalletSDK.ProximityElementReference> = requiredFields.isEmpty ? [] : [
                .init(namespace: portrait.namespace, elementIdentifier: portrait.elementIdentifier),
            ]
            XCTAssertEqual(projected.requiredElements, expected)
            XCTAssertEqual(projected.credentialOptions.count, 1)
        }
    }
}
#endif
