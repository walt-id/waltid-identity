#if canImport(WalletCore) && os(iOS)
import XCTest
@preconcurrency import WalletCore
@testable import WalletSDK

final class ProximityBridgeContractTests: XCTestCase {
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
