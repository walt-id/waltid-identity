import CoreBluetooth
import XCTest
@preconcurrency import WalletCore
@testable import WalletSDK

final class ProximityPhysicalDeviceTests: XCTestCase {
    func testSuccessDisconnectAndFreshRecovery() async throws {
        executionTimeAllowance = 240
        let run = try PhysicalPreflight()
        try await run.event("holder-started", ["configuration": run.configuration, "processId": String(ProcessInfo.processInfo.processIdentifier)])
        try await requestBluetoothAuthorization()
        let nfc = IOSNfcHostPlatformAdapter()
        let holder = try await WalletCore.PhysicalProximityHolder.companion.create(nfcHost: nfc)
        do {
            try await withThrowingTaskGroup(of: Void.self) { group in
                group.addTask {
                    try await Task.sleep(nanoseconds: 210_000_000_000)
                    throw PhysicalPrecondition.failed("Physical test deadline exceeded")
                }
                group.addTask { [self] in
                    for round in 1...3 { try await exchange(holder: holder, nfc: nfc, run: run, round: round) }
                    try await run.event("holder-passed", ["rounds": "3"])
                }
                defer { group.cancelAll() }
                try await group.next()
            }
            try await holder.close()
        } catch {
            try await holder.close()
            throw error
        }
    }

    private func exchange(holder: WalletCore.PhysicalProximityHolder, nfc: IOSNfcHostPlatformAdapter,
                          run: PhysicalPreflight, round: Int) async throws {
        let configuration = configuration(run.configuration)
        let core = try await holder.start(configuration: configuration.toKMPConfiguration())
        let session = WalletSDK.ProximitySession(bridge: KMPProximityPresentationSessionBridge(session: core, nfcHost: nfc))
        var approvedReview: WalletSDK.ProximityReview?
        var approvedSubmission: WalletSDK.ProximitySubmission?
        var completed = false
        var presentment: Task<Void, Never>?
        do {
            for await state in session.states {
                switch state {
                case .engagementReady(let engagements):
                    let qr = engagements.compactMap { value -> String? in
                        if case .qr(let payload) = value { return payload }; return nil
                    }.first ?? ""
                    try await run.event("holder-ready-\(round)", ["qr": qr, "root": holder.rootHex, "wrongRoot": holder.wrongRootHex])
                    if run.configuration == "nfc-ble-continuation" && presentment == nil {
                        presentment = Task { await session.presentNfc() }
                    }
                case .reviewRequired(let review, _):
                    let option = try XCTUnwrap(review.documents.first?.credentialOptions.first)
                    XCTAssertEqual(Set(option.requestedElements.map(\.elementIdentifier)), ["given_name", "family_name"])
                    let fields: Set<WalletSDK.ProximityElementReference> = Set(["given_name", "family_name"].map {
                        .init(namespace: "org.iso.18013.5.1", elementIdentifier: $0)
                    })
                    let submission = try WalletSDK.ProximitySubmission(documents: [
                        .init(requestIndex: 0, credentialID: "peer-mdl", disclosedElements: fields)
                    ])
                    approvedReview = review
                    approvedSubmission = submission
                    let route = try XCTUnwrap(session.connectedRoute)
                    XCTAssertEqual(route.transport, .bluetoothLowEnergy)
                    XCTAssertEqual(route.engagement, run.configuration == "nfc-ble-continuation" ? .nfc : .qr)
                    try await run.event("holder-review-\(round)", ["engagement": run.configuration == "nfc-ble-continuation" ? "Nfc" : "Qr",
                                                                    "transport": "BluetoothLowEnergy"])
                    if round != 2 {
                        try await run.awaitApproval(round: round)
                        let result = try await session.dispatch(.approve(reviewID: review.reviewID, submission: submission))
                        XCTAssertEqual(result, .accepted)
                    }
                case .completed(_, let declined, let receipt):
                    XCTAssertNotEqual(round, 2, "Disconnect control disclosed data")
                    XCTAssertFalse(declined)
                    XCTAssertEqual(try XCTUnwrap(receipt).submission, approvedSubmission)
                    try await run.event("holder-completed-\(round)", ["approvedFieldCount": "2"])
                    completed = true
                case .failed, .cancelled:
                    guard round == 2, let review = approvedReview, let submission = approvedSubmission else {
                        throw PhysicalPrecondition.failed("Physical holder failed outside the review-disconnect control")
                    }
                    XCTAssertFalse(state.legalActions.contains(.approve))
                    guard case .rejected = try await session.dispatch(.approve(reviewID: review.reviewID, submission: submission)) else {
                        throw PhysicalPrecondition.failed("Stale review remained approvable after physical disconnect")
                    }
                    try await run.event("holder-rejected-\(round)", ["disclosed": "false", "approvalRejected": "true"])
                    completed = true
                case .preparationRequired:
                    throw PhysicalPrecondition.failed("This host requires a separate prepared-sharing procedure")
                default: break
                }
            }
            XCTAssertTrue(completed, "Physical suite ended without a terminal assertion")
            await session.close()
            presentment?.cancel()
        } catch {
            await session.close()
            presentment?.cancel()
            throw error
        }
    }

    private func configuration(_ name: String) -> WalletSDK.ProximityConfiguration {
        let ble = WalletSDK.ProximityBLEConfiguration(roles: name.hasSuffix("peripheral") ? .peripheralServer : .centralClient,
            bearerPolicy: name.hasPrefix("ble-l2cap") ? .preferL2CAP : .gattOnly)
        return .init(session: name == "nfc-ble-continuation"
            ? .nfc(.init(handover: .staticHandover, retrieval: .init(bluetoothLowEnergy: ble)))
            : .qr(.init(bluetoothLowEnergy: ble)))
    }

    @MainActor
    private func requestBluetoothAuthorization() async throws {
        let manager = CBCentralManager(delegate: nil, queue: .main)
        defer { manager.stopScan() }
        let deadline = Date().addingTimeInterval(30)
        while CBManager.authorization == .notDetermined && Date() < deadline {
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        guard CBManager.authorization == .allowedAlways else {
            throw PhysicalPrecondition.failed("Bluetooth authorization was not granted for the disposable host")
        }
    }
}
