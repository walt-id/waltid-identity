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

    @MainActor
    func testPublicCAImportRequiresReviewAndCancelDoesNotPersist() async throws {
        let persistence = InMemoryDemoReaderTrustSettingsPersistence()
        let controller = DemoReaderTrustSettingsController(persistence: persistence)
        controller.setReaderPolicy(.requireTrusted)
        let certificate = try XCTUnwrap(Data(base64Encoded: Self.testReaderCaDerBase64))

        await controller.prepareImport(
            sourceName: "wal-1349-local-reader-ca.der",
            data: certificate
        )

        let preview = try XCTUnwrap(controller.pendingImport)
        XCTAssertEqual(preview.sourceName, "wal-1349-local-reader-ca.der")
        XCTAssertEqual(preview.readerAuthorities.count, 1)
        XCTAssertEqual(
            preview.readerAuthorities.first?.displayName,
            "CN=WAL-1349 Local Reader Test CA"
        )
        XCTAssertEqual(
            preview.readerAuthorities.first?.subject,
            "CN=WAL-1349 Local Reader Test CA"
        )
        XCTAssertEqual(
            preview.readerAuthorities.first?.sha256Fingerprint,
            Self.testReaderCaSHA256
        )
        XCTAssertTrue(controller.settings.trustAnchors.isEmpty)
        XCTAssertTrue(
            try ProximityReaderTrustSettingsCodec.decode(
                XCTUnwrap(persistence.encodedSettings)
            ).trustAnchors.isEmpty
        )

        controller.cancelImport()

        XCTAssertNil(controller.pendingImport)
        XCTAssertTrue(controller.settings.trustAnchors.isEmpty)
        XCTAssertEqual(controller.settings.readerPolicy, .requireTrusted)

        await controller.prepareImport(
            sourceName: "wal-1349-local-reader-ca.der",
            data: certificate
        )
        controller.confirmImport()

        XCTAssertNil(controller.pendingImport)
        XCTAssertEqual(controller.settings.trustAnchors.count, 1)
        XCTAssertEqual(
            try ProximityReaderTrustSettingsCodec.decode(
                XCTUnwrap(persistence.encodedSettings)
            ).trustAnchors.count,
            1
        )
    }

    func testFilePickerCancellationIsSilent() throws {
        let selection = try ReaderTrustImportFileLoader.load(
            .failure(CocoaError(.userCancelled))
        )

        XCTAssertEqual(selection, .cancelled)
    }

    func testSwiftSettingsApplyReaderPolicyToSessionConfiguration() {
        let settings = ProximityReaderTrustSettings(readerPolicy: .requireTrusted)

        let configuration = settings.applying()

        XCTAssertEqual(configuration.readerPolicy, .requireTrusted)
        XCTAssertNil(configuration.readerTrustEvaluator)
    }

    // Public certificate generated and owned by walt.id for WAL-1349 qualification tests.
    // No private key or third-party fixture material is embedded here.
    private static let testReaderCaDerBase64 =
        "MIIB5jCCAYygAwIBAgIIQAAAAAAAAAIwCgYIKoZIzj0EAwIwKDEmMCQGA1UEAwwdV0FMLTEzNDkgTG9jYWwgUmVhZGVyIFRlc3QgQ0EwHhcNMjYwOTAxMDgxNTIyWhcNMzYwODI5MDgxNTIyWjAoMSYwJAYDVQQDDB1XQUwtMTM0OSBMb2NhbCBSZWFkZXIgVGVzdCBDQTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABLkoxDaSw3orgCt+rU6tkUzMqbvwbGSW79yUDGFF7/RACZJuY33ELFPTTZnx6vYGuVFZ4DiMI8a7YfPwQRY4mVajgZ8wgZwwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFI7/672ZcKzVj4pzE9lFgmc6kpFvMFcGA1UdIwRQME6AFI7/672ZcKzVj4pzE9lFgmc6kpFvoSykKjAoMSYwJAYDVQQDDB1XQUwtMTM0OSBMb2NhbCBSZWFkZXIgVGVzdCBDQYIIQAAAAAAAAAIwCgYIKoZIzj0EAwIDSAAwRQIhAKrZrpvBEYeWpezCh6b48gvPzaHLXUbGfmOApayRI9MVAiBds/mL9fhhsBWtlFj2LSaMGsuPYVVIbT2d3YeWSVrJxg=="

    private static let testReaderCaSHA256 =
        "6C:5B:A7:9B:60:AF:AE:DE:74:4C:DF:E6:7F:EB:A1:51:" +
        "DE:5D:89:D7:D2:5B:20:1E:8E:94:CC:CB:AE:78:52:09"
}
