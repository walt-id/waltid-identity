#if canImport(WalletCore) && os(iOS)
import XCTest
@preconcurrency import WalletCore
@testable import WalletSDK

final class KMPProximityProjectionTests: XCTestCase {
    #if WALLET_SDK_BRIDGE_FIXTURES
    func testRealBridgeStreamsReviewAndForwardsEverySelectedField() async throws {
        let fields = ["given_name", "family_name"]
        let core = WalletCore.ProximityBridgeTestSession()
        let bridge = KMPProximityPresentationSessionBridge(session: core.session, nfcHost: IOSNfcHostPlatformAdapter())
        let session = WalletSDK.ProximitySession(bridge: bridge)
        var iterator = session.states.makeAsyncIterator()
        guard case let .reviewRequired(projected, _) = await iterator.next() else {
            return XCTFail("The real KMP bridge did not publish review")
        }
        XCTAssertEqual(projected.documents[0].credentialOptions[0].requestedElements.map(\.elementIdentifier), fields)
        let selected: Set<WalletSDK.ProximityElementReference> = Set(fields.map {
            .init(namespace: "org.iso.18013.5.1", elementIdentifier: $0)
        })
        let result = try await session.dispatch(.approve(reviewID: projected.reviewID, submission: .init(documents: [
            .init(requestIndex: 0, credentialID: "credential-1", disclosedElements: selected)
        ])))
        XCTAssertEqual(result, .accepted)
        let forwarded = try XCTUnwrap(core.lastApproval)
        XCTAssertEqual(forwarded.reviewId.value, projected.reviewID.value)
        XCTAssertEqual(forwarded.submission.documents.count, 1)
        XCTAssertEqual(forwarded.submission.toSwiftSubmission().documents[0].disclosedElements, selected)
        let nextState1 = await iterator.next()
        XCTAssertEqual(nextState1, .sendingResponse(exchange: 1))
        let beforeCompletion = Date()
        core.completeResponse()
        let nextState2 = await iterator.next()
        guard case let .completed(exchanges, declined, receipt) = nextState2 else {
            return XCTFail("The real receipt did not cross the bridge")
        }
        XCTAssertEqual(exchanges, 1)
        XCTAssertFalse(declined)
        let actualReceipt = try XCTUnwrap(receipt)
        XCTAssertEqual(actualReceipt.review, projected)
        XCTAssertEqual(actualReceipt.submission.documents[0].disclosedElements, selected)
        XCTAssertEqual(actualReceipt.approvalTiming, .duringConnection)
        XCTAssertGreaterThanOrEqual(actualReceipt.completedAt, beforeCompletion.addingTimeInterval(-1))
        XCTAssertLessThanOrEqual(actualReceipt.completedAt, Date().addingTimeInterval(1))
        let nextState3 = await iterator.next()
        XCTAssertNil(nextState3)
        await session.close()
        XCTAssertEqual(core.closeCalls, 1)
    }

    func testRealBridgeCancellationRemovesReviewAndStopsLaterStates() async throws {
        let core = WalletCore.ProximityBridgeTestSession()
        let bridge = KMPProximityPresentationSessionBridge(session: core.session, nfcHost: IOSNfcHostPlatformAdapter())
        let session = WalletSDK.ProximitySession(bridge: bridge)
        var iterator = session.states.makeAsyncIterator()
        guard case let .reviewRequired(review, reason) = await iterator.next() else {
            return XCTFail("Missing projected review")
        }
        XCTAssertEqual(reason, .preparedSharingChanged)
        XCTAssertTrue(review.documents[0].requiredElements.isEmpty)
        XCTAssertTrue(review.readerAuthentication.isEmpty)
        XCTAssertTrue(review.useCases.isEmpty)
        XCTAssertTrue(review.applicationAuthorizations.isEmpty)
        let result = try await session.dispatch(.cancel)
        XCTAssertEqual(result, .accepted)
        let nextState4 = await iterator.next()
        XCTAssertEqual(nextState4, .cancelled)
        core.publishLateReview()
        let nextState5 = await iterator.next()
        XCTAssertNil(nextState5)
        XCTAssertEqual(core.cancelCalls, 1)
        await session.close()
    }

    #endif

    func testFailedStatePreservesCodeRecoveryAndHasNoLegalActions() throws {
        let error = WalletCore.ProximityError(category: .transport, code: "peer_disconnected",
            message: "Reader disconnected", recovery: .startNewSession)
        let projected = try WalletCore.ProximityStateFailed(error: error).toSwiftState()
        XCTAssertEqual(projected, .failed(.init(category: .transport, code: "peer_disconnected",
            message: "Reader disconnected", recovery: .startNewSession)))
        XCTAssertTrue(projected.legalActions.isEmpty)
    }

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
