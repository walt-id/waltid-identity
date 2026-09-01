import Foundation
import XCTest
@testable import iosApp
@testable import WalletSDK

final class DemoReaderTrustSettingsTests: XCTestCase {
    @MainActor
    func testPolicyPersistsWithCanonicalCodecAndLoadsInNewController() throws {
        let persistence = InMemoryDemoReaderTrustSettingsPersistence()
        let first = DemoReaderTrustSettingsController(persistence: persistence)

        first.setReaderPolicy(.requireTrusted)

        let encoded = try XCTUnwrap(persistence.encodedSettings)
        XCTAssertTrue(encoded.contains("\"version\":1"))
        XCTAssertTrue(encoded.contains("\"readerPolicy\":\"require_trusted\""))
        let reloaded = DemoReaderTrustSettingsController(persistence: persistence)
        XCTAssertEqual(reloaded.settings.readerPolicy, .requireTrusted)
        XCTAssertNil(reloaded.errorMessage)
    }

    @MainActor
    func testInvalidStoredSettingsFailClosedToDefaultsWithVisibleError() {
        let persistence = InMemoryDemoReaderTrustSettingsPersistence()
        persistence.encodedSettings = "{\"version\":99}"

        let controller = DemoReaderTrustSettingsController(persistence: persistence)

        XCTAssertEqual(controller.settings, ProximityReaderTrustSettings())
        XCTAssertNotNil(controller.errorMessage)
    }

    @MainActor
    func testInvalidFileImportProducesRecoverableErrorWithoutChangingSettings() async {
        let persistence = InMemoryDemoReaderTrustSettingsPersistence()
        let controller = DemoReaderTrustSettingsController(persistence: persistence)

        await controller.prepareImport(
            sourceName: "not-a-certificate.der",
            data: Data("not a certificate".utf8)
        )

        XCTAssertEqual(controller.settings, ProximityReaderTrustSettings())
        XCTAssertNil(controller.pendingImport)
        XCTAssertNotNil(controller.errorMessage)
        XCTAssertFalse(controller.importInProgress)
    }

    func testSwiftSettingsApplyReaderPolicyToSessionConfiguration() {
        let settings = ProximityReaderTrustSettings(readerPolicy: .requireTrusted)

        let configuration = settings.applying()

        XCTAssertEqual(configuration.readerPolicy, .requireTrusted)
        XCTAssertNil(configuration.readerTrustEvaluator)
    }
}
