import Foundation
import XCTest
@testable import iosApp

final class DemoPinStoreTests: XCTestCase {
    func testDerivesAndVerifiesIndependentPbkdf2Vector() async throws {
        let salt = Data(base64Encoded: Self.paritySaltB64)!
        let derived = UserDefaultsDemoPinStore.derive(
            pin: Self.parityPin,
            salt: salt,
            iterations: UserDefaultsDemoPinStore.iterations
        )
        XCTAssertEqual(derived?.base64EncodedString(), Self.parityVerifierB64)

        let defaults = UserDefaults(suiteName: "pin-parity-\(UUID().uuidString)")!
        let store = UserDefaultsDemoPinStore(
            walletID: "parity",
            defaults: defaults,
            randomSalt: { salt }
        )
        try await store.setPin(Self.parityPin)

        XCTAssertEqual(defaults.string(forKey: "id.walt.walletdemo.pin.parity"), Self.parityRecord)
        XCTAssertTrue(await store.verifyPin(Self.parityPin))
        XCTAssertFalse(await store.verifyPin("0000"))
    }

    func testSetPinFailsWhenRandomGenerationFails() async {
        let defaults = UserDefaults(suiteName: "pin-rng-\(UUID().uuidString)")!
        let store = UserDefaultsDemoPinStore(
            walletID: "rng",
            defaults: defaults,
            randomSalt: { throw DemoPinRecordError.randomGenerationFailed }
        )

        do {
            try await store.setPin(Self.parityPin)
            XCTFail("Expected random generation failure")
        } catch DemoPinRecordError.randomGenerationFailed {
            XCTAssertNil(defaults.string(forKey: "id.walt.walletdemo.pin.rng"))
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testRejectsTruncatedAndWrongVersionRecords() async {
        let defaults = UserDefaults(suiteName: "pin-reject-\(UUID().uuidString)")!
        let store = UserDefaultsDemoPinStore(walletID: "reject", defaults: defaults)

        defaults.set(
            "2:210000:\(Self.paritySaltB64):\(Self.parityVerifierB64)",
            forKey: "id.walt.walletdemo.pin.reject"
        )
        XCTAssertFalse(await store.verifyPin(Self.parityPin))

        defaults.set("1:210000:\(Self.paritySaltB64)", forKey: "id.walt.walletdemo.pin.reject")
        XCTAssertFalse(await store.verifyPin(Self.parityPin))

        defaults.set(
            "1:210000:not-base64:\(Self.parityVerifierB64)",
            forKey: "id.walt.walletdemo.pin.reject"
        )
        XCTAssertFalse(await store.verifyPin(Self.parityPin))
    }

    private static let parityPin = "1234"
    private static let paritySaltB64 = "ABEiM0RVZneImaq7zN3u/w=="
    private static let parityVerifierB64 = "Gu7nstzpe35HRTn195Op0D2/xfRyYcLn+RPSjTlSZVE="
    private static let parityRecord = "1:210000:\(paritySaltB64):\(parityVerifierB64)"
}
