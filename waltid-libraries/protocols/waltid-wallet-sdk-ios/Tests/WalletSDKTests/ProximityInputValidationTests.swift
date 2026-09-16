import XCTest
@testable import WalletSDK

final class ProximityInputValidationTests: XCTestCase {
    func testConfigurationRejectsInvalidLimitsAndProfilePolicyRecoverably() throws {
        for limit in [Int.min, 0, 16_777_217, Int.max] {
            assertInvalid { try ProximityConfiguration(maximumMessageBytes: limit) }
        }
        for limit in [1, 16_777_216] {
            XCTAssertEqual(try ProximityConfiguration(maximumMessageBytes: limit).maximumMessageBytes, limit)
        }
        assertInvalid { try ProximityConfiguration(profile: .eudiARF3FCAF202608) }
        XCTAssertEqual(try ProximityConfiguration(profile: .eudiARF3FCAF202608,
            readerPolicy: .requireTrusted).readerPolicy, .requireTrusted)
        XCTAssertEqual(ProximityConfiguration().maximumMessageBytes, 1_048_576)
    }

    func testTrustDecisionRejectsIncoherentEvidenceWithoutGrantingTrust() throws {
        assertInvalid { try ProximityReaderTrustDecision(state: .notEvaluated) }
        assertInvalid { try ProximityReaderTrustDecision(state: .trusted) }
        assertInvalid { try ProximityReaderTrustDecision(state: .revoked) }
        assertInvalid { try ProximityReaderTrustDecision(state: .validButUntrusted, revocation: .revoked) }
        assertInvalid { try ProximityReaderTrustDecision(state: .trusted, certificatePath: .valid,
            revocation: .indeterminate) }
        assertInvalid { try ProximityReaderTrustDecision(state: .validButUntrusted, displayName: " \n") }
        XCTAssertEqual(try ProximityReaderTrustDecision(state: .trusted,
            certificatePath: .valid).state, .trusted)
    }

    func testProfileAuthorizationRejectsIncompleteOrUnboundHostResults() throws {
        assertInvalid { try ProximityApplicationAuthorizationDetail(id: "", label: "Label", value: "Value") }
        let detail = try ProximityApplicationAuthorizationDetail(id: "amount", label: "Amount", value: "10")
        let digest = Data(repeating: 0, count: 32)
        assertInvalid { try ProximityApplicationAuthorization(profileID: "profile", displayTitle: "Title",
            details: [], compatibleCredentialIDs: ["credential"], resultBindingDigest: digest) }
        assertInvalid { try ProximityApplicationAuthorization(profileID: "profile", displayTitle: "Title",
            details: [detail, detail], compatibleCredentialIDs: ["credential"], resultBindingDigest: digest) }
        assertInvalid { try ProximityApplicationAuthorization(profileID: "profile", displayTitle: "Title",
            details: [detail], compatibleCredentialIDs: ["credential"], resultBindingDigest: Data()) }
        let foreign = try ProximityDeviceSignedElement(credentialID: "other", namespace: "ns",
            elementIdentifier: "amount", valueCBOR: Data([0x01]))
        assertInvalid { try ProximityApplicationAuthorization(profileID: "profile", displayTitle: "Title",
            details: [detail], compatibleCredentialIDs: ["credential"], deviceSignedElements: [foreign],
            resultBindingDigest: digest) }
        let valid = try ProximityApplicationAuthorization(profileID: "profile", displayTitle: "Title",
            details: [detail], compatibleCredentialIDs: ["credential"], resultBindingDigest: digest)
        XCTAssertEqual(valid.resultBindingDigest, digest)
    }

    func testDisclosureRejectsEmptyAndDuplicateSelectionsRecoverably() throws {
        assertInvalid { try ProximityElementReference(namespace: "", elementIdentifier: "name") }
        let element = try ProximityElementReference(namespace: "ns", elementIdentifier: "name")
        assertInvalid { try ProximityDocumentSubmission(requestIndex: -1, credentialID: "id", disclosedElements: [element]) }
        assertInvalid { try ProximityDocumentSubmission(requestIndex: 0, credentialID: "id", disclosedElements: []) }
        assertInvalid { try ProximitySubmission(documents: []) }
        let document = try ProximityDocumentSubmission(requestIndex: 0, credentialID: "id", disclosedElements: [element])
        assertInvalid { try ProximitySubmission(documents: [document, document]) }
        XCTAssertEqual(try ProximitySubmission(documents: [document]).documents, [document])
    }

    private func assertInvalid<T>(_ operation: () throws -> T, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertThrowsError(try operation(), file: file, line: line) { error in
            guard case WalletError.invalidInput = error else {
                return XCTFail("Expected recoverable invalid-input error, got \(error)", file: file, line: line)
            }
        }
    }
}
