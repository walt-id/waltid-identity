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
        let parityMatches = try await store.verifyPin(Self.parityPin)
        let wrongPinMatches = try await store.verifyPin("0000")
        XCTAssertTrue(parityMatches)
        XCTAssertFalse(wrongPinMatches)
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

    func testCorruptAndMissingRecordsReportStorageFailure() async {
        let defaults = UserDefaults(suiteName: "pin-reject-\(UUID().uuidString)")!
        let store = UserDefaultsDemoPinStore(walletID: "reject", defaults: defaults)
        for record in [nil, "2:210000:\(Self.paritySaltB64):\(Self.parityVerifierB64)",
                       "1:210000:\(Self.paritySaltB64)", "1:210000:not-base64:\(Self.parityVerifierB64)"] as [String?] {
            defaults.set(record, forKey: "id.walt.walletdemo.pin.reject")
            do { _ = try await store.verifyPin(Self.parityPin); XCTFail("Expected PIN storage failure") }
            catch DemoPinRecordError.invalidRecord { }
            catch DemoPinRecordError.missingRecord { }
            catch { XCTFail("Unexpected error: \(error)") }
        }
    }

    private static let parityPin = "1234"
    private static let paritySaltB64 = "ABEiM0RVZneImaq7zN3u/w=="
    private static let parityVerifierB64 = "Gu7nstzpe35HRTn195Op0D2/xfRyYcLn+RPSjTlSZVE="
    private static let parityRecord = "1:210000:\(paritySaltB64):\(parityVerifierB64)"
}
