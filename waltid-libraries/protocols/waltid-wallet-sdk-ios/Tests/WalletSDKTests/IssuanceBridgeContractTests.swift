#if canImport(WalletCore) && os(iOS)
import XCTest
@preconcurrency import WalletCore
@testable import WalletSDK

final class IssuanceBridgeContractTests: XCTestCase {
    func testExplicitSelectionPreservesDatasetAndEveryHolderBinding() throws {
        let selection = try IssuanceCredentialSelection(configurationID: "identity", credentialIdentifier: "dataset-a",
            holders: .existing([.init(keyID: "first", did: "did:key:first"), .init(keyID: "second")]))
        let core = try selection.toKMPSelection()
        XCTAssertEqual(core.credentialConfigurationId, "identity")
        XCTAssertEqual(core.credentialIdentifier, "dataset-a")
        XCTAssertEqual((core.holders as! MobileWalletCredentialHoldersExisting).bindings.map(\.keyId), ["first", "second"])
        XCTAssertEqual((core.holders as! MobileWalletCredentialHoldersExisting).bindings.map(\.did), ["did:key:first", nil])
    }

    func testGeneratedSelectionPreservesCountAndRejectsInvalidNativeCounts() throws {
        let selection = try IssuanceCredentialSelection(configurationID: "identity", holders: .newKeys(count: 2))
        let core = try selection.toKMPSelection()
        XCTAssertEqual((core.holders as! MobileWalletCredentialHoldersNewKeys).count, 2)
        for count in [0, -1, Int.max] {
            XCTAssertThrowsError(try IssuanceCredentialSelection(configurationID: "identity", holders: .newKeys(count: count)).toKMPSelection())
        }
    }

    func testSelectionRejectsEmptyOrBlankBindingsBeforeCallingKotlin() {
        for holders in [IssuanceCredentialHolders.existing([]), .existing([.init(keyID: " ")]),
                        .existing([.init(keyID: "key", did: " ")])] {
            XCTAssertThrowsError(try IssuanceCredentialSelection(configurationID: "identity", holders: holders))
        }
        XCTAssertThrowsError(try IssuanceCredentialSelection(configurationID: " ", holders: .newKeys(count: 1)))
        XCTAssertThrowsError(try IssuanceCredentialSelection(configurationID: "identity", credentialIdentifier: " ", holders: .newKeys(count: 1)))
    }

    func testUncertainOutcomesRemainDistinctFromRetryableNetworkAndStorageFailures() throws {
        let codes: [(Waltid_openid4vc_walletWalletIssuanceErrorCode, IssuanceErrorCode)] = [
            (.network, .network), (.storage, .storage),
            (.remoteOutcomeUncertain, .remoteOutcomeUncertain), (.storageOutcomeUncertain, .storageOutcomeUncertain),
        ]
        let pending = Waltid_openid4vc_walletWalletIssuanceContinuation(id: "handle",
            credentialConfigurationId: "identity", intervalSeconds: nil, credentialIdentifier: nil)
        for (coreCode, swiftCode) in codes {
            let core = Waltid_openid4vc_walletWalletIssuanceOutcomeFailed(sessionId: "session",
                error: .init(code: coreCode, message: "Retain this handle"), storedCredentialIds: ["stored"],
                deferredCredentials: [pending], failure: nil)
            guard case let .failed(_, error, stored, deferred) = try core.toSwiftIssuanceOutcome() else {
                return XCTFail("Expected an explicit failure outcome")
            }
            XCTAssertEqual(error.code, swiftCode)
            XCTAssertEqual(stored, ["stored"])
            XCTAssertEqual(deferred.map(\.id), ["handle"])
        }
    }

    func testConfiguredDeferredOutcomePreservesTheReleasedReference() throws {
        let pending = Waltid_openid4vc_walletWalletDeferredCredential(id: "pending",
            credentialConfigurationId: "identity", intervalSeconds: .init(longLong: 7), credentialIdentifier: "dataset-a")
        let core = Waltid_openid4vc_walletWalletIssuanceOutcomeDeferred(sessionId: "session",
            storedCredentialIds: ["stored"], credentials: [pending])
        guard case let .deferred(session, stored, handles) = try core.toSwiftIssuanceOutcome() else {
            return XCTFail("Expected configured deferred issuance")
        }
        XCTAssertEqual(session, "session")
        XCTAssertEqual(stored, ["stored"])
        XCTAssertEqual(handles, [.init(id: "pending", credentialConfigurationID: "identity", intervalSeconds: 7,
            credentialIdentifier: "dataset-a")])
    }

    func testLocalStorageHandlePreservesAnUnknownConfiguration() throws {
        let pending = Waltid_openid4vc_walletWalletIssuanceContinuation(id: "local-save",
            credentialConfigurationId: nil, intervalSeconds: nil, credentialIdentifier: nil)
        let core = Waltid_openid4vc_walletWalletIssuanceOutcomeFailed(sessionId: "session",
            error: .init(code: .storage, message: "Retry local storage"), storedCredentialIds: [],
            deferredCredentials: [pending], failure: nil)
        guard case let .failed(_, _, _, handles) = try core.toSwiftIssuanceOutcome() else {
            return XCTFail("Expected local save recovery")
        }
        XCTAssertEqual(handles.map(\.id), ["local-save"])
        XCTAssertNil(handles.first?.credentialConfigurationID)
        XCTAssertNil(handles.first?.credentialIdentifier)
    }

    func testEveryFailureStageRetainsStoredIdsDeferredHandlesAndUnattemptedTargets() throws {
        let stages: [(Waltid_openid4vc_walletCredentialIssuanceStage, IssuanceFailureStage)] = [
            (.proof, .proof), (.request, .request), (.response, .response), (.storage, .storage), (.observer, .observer),
        ]
        let pending = Waltid_openid4vc_walletWalletIssuanceContinuation(id: "handle",
            credentialConfigurationId: "identity", intervalSeconds: .init(longLong: 7), credentialIdentifier: "dataset-a")
        let stopped = Waltid_openid4vci_walletCredentialIssuanceTarget(credentialConfigurationId: "identity", credentialIdentifier: "dataset-b")
        let unattempted = Waltid_openid4vci_walletCredentialIssuanceTarget(credentialConfigurationId: "identity", credentialIdentifier: "dataset-c")
        for (coreStage, swiftStage) in stages {
            let core = Waltid_openid4vc_walletWalletIssuanceOutcomeFailed(sessionId: "session",
                error: .init(code: .issuerResponse, message: "Issuance stopped"), storedCredentialIds: ["stored"],
                deferredCredentials: [pending], failure: .init(target: stopped, stage: coreStage, notAttempted: [unattempted]))
            guard case let .failed(session, error, stored, deferred) = try core.toSwiftIssuanceOutcome() else {
                return XCTFail("Expected failure with retained progress")
            }
            XCTAssertEqual(session, "session")
            XCTAssertEqual(stored, ["stored"])
            XCTAssertEqual(deferred, [.init(id: "handle", credentialConfigurationID: "identity", intervalSeconds: 7,
                credentialIdentifier: "dataset-a")])
            XCTAssertEqual(error.targetFailure?.stage, swiftStage)
            XCTAssertEqual(error.targetFailure?.target.credentialIdentifier, "dataset-b")
            XCTAssertEqual(error.targetFailure?.notAttempted.map(\.credentialIdentifier), ["dataset-c"])
        }
    }
}
#endif
