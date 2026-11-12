import UIKit
import XCTest
@preconcurrency import WalletCore

final class ScaPhysicalDeviceTests: XCTestCase {
    @MainActor
    func testTimedPaymentAuthorization() async throws {
        #if targetEnvironment(simulator)
        throw XCTSkip("Requires physical Secure Enclave and biometrics")
        #else
        guard ProcessInfo.processInfo.environment["WALLET_SCA_TIMED"] == "1" else {
            throw XCTSkip("Requires explicit local biometric operator opt-in")
        }
        executionTimeAllowance = 600
        let result = try await PhysicalScaExercise().runTimed(operator: ScaOperator())
        XCTAssertTrue(result.hasPrefix("PASS timed-production-path"), result)
        let receipt = XCTAttachment(string: result)
        receipt.lifetime = .keepAlways
        add(receipt)
        #endif
    }
}

private final class ScaOperator: NSObject, PhysicalScaOperator {
    func __beforeStep(step: String, completionHandler: @escaping (Error?) -> Void) {
        Task { @MainActor in
            guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
                  let root = scene.windows.first(where: \.isKeyWindow)?.rootViewController else {
                completionHandler(NSError(domain: "ScaPhysicalTest", code: 1,
                                          userInfo: [NSLocalizedDescriptionKey: "No foreground operator window"]))
                return
            }
            let alert = UIAlertController(title: "Synthetic payment test",
                message: "EUR 12.50 to Synthetic shop.\n\n\(step)", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Start step", style: .default) { _ in completionHandler(nil) })
            root.present(alert, animated: false)
        }
    }
}
