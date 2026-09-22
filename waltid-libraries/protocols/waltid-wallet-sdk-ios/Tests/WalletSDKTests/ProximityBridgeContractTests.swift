#if canImport(WalletCore) && os(iOS)
import XCTest
@preconcurrency import WalletCore
@testable import WalletSDK

final class ProximityBridgeContractTests: XCTestCase {
    func testAggregateRoutesAndRemediationOrderMatchKMP() throws {
        let ble = WalletSDK.ProximityRetrievalOptions()
        let wifi = WalletSDK.ProximityRetrievalOptions(bluetoothLowEnergy: nil, wifiAware: true)
        let cases: [(WalletSDK.ProximitySessionConfiguration, [Bool])] = [
            (.qr(ble), [true, false, true, false, false, false]),
            (.nfc(.init(handover: .negotiatedHandover, retrieval: ble, qrFallback: wifi)),
                [true, true, true, false, false, true]),
            (.provisionalNFCV2(.init(qrFallback: wifi, wifiAware: true)),
                [true, true, false, false, true, true]),
        ]
        for (session, selected) in cases {
            let configuration = try WalletSDK.ProximityConfiguration(session: session).toKMPConfiguration()
            for mask in 0..<64 {
                var observations: [WalletCore.ProximityTransportCapability] = []
                for index in 0..<6 {
                    // Same-channel NFCv2 retrieval cannot outlive its engagement.
                    let available = mask & (1 << index) != 0 && (index != 4 || mask & 2 != 0)
                    let runtime: any WalletCore.ProximityRuntimeObservation = available
                        ? WalletCore.ProximityRuntimeObservationAvailable.shared
                        : WalletCore.ProximityRuntimeObservationUnavailable(
                            error: .init(category: .capability, code: "unavailable", message: "Unavailable",
                                         recovery: .retryPrerequisites),
                            remediationActions: index.isMultiple(of: 2) ? [.retry] : [.requestBluetoothPermission, .retry])
                    observations.append(.init(implemented: true, profilePermitted: true,
                                              runtime: runtime, selected: selected[index]))
                }
                let core = WalletCore.ProximityCapabilities(
                    profile: configuration.profile, session: configuration.session,
                    qrEngagement: observations[0], nfcEngagement: observations[1],
                    bluetoothLowEnergy: observations[2], nfcRetrieval: observations[3],
                    nfcV2Retrieval: observations[4], wifiAwareRetrieval: observations[5])
                let swift = core.toSwiftCapabilities()
                XCTAssertEqual(swift.mayStart, core.mayStart)
                XCTAssertEqual(swift.qrMayStart, core.qrMayStart)
                XCTAssertEqual(swift.nfcMayStart, core.nfcMayStart)
                XCTAssertEqual(swift.remediationActions, core.remediationActions.map { $0.toSwiftAction() })
            }
        }
    }

    func testTransportDecisionsMatchKMPForEveryFlagAndRuntimeState() {
        let unavailable = WalletCore.ProximityRuntimeObservationUnavailable(
            error: .init(category: .capability, code: "permission", message: "Permission required",
                         recovery: .retryPrerequisites),
            remediationActions: [.requestBluetoothPermission, .openApplicationSettings]
        )
        let observations: [any WalletCore.ProximityRuntimeObservation] = [
            WalletCore.ProximityRuntimeObservationNotChecked.shared,
            WalletCore.ProximityRuntimeObservationAvailable.shared,
            unavailable,
        ]
        for implemented in [false, true] {
            for permitted in [false, true] {
                for selected in [false, true] {
                    for runtime in observations {
                        let core = WalletCore.ProximityTransportCapability(
                            implemented: implemented, profilePermitted: permitted,
                            runtime: runtime, selected: selected
                        )
                        let swift = core.toSwiftCapability()
                        XCTAssertEqual(swift.mayStart, core.mayStart)
                        XCTAssertEqual(swift.runtimeAvailable, core.runtimeAvailable)
                        XCTAssertEqual(swift.remediationActions.count, core.remediationActions.count)
                        if runtime is WalletCore.ProximityRuntimeObservationUnavailable {
                            XCTAssertEqual(swift.remediationActions, [.requestBluetoothPermission, .openApplicationSettings])
                        } else {
                            XCTAssertTrue(swift.remediationActions.isEmpty)
                        }
                    }
                }
            }
        }
    }
}
#endif
