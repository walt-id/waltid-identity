import Foundation
import XCTest
import SwiftUI
import UIKit
@testable import iosApp
@testable import WalletSDK

final class DemoReaderTrustSettingsTests: XCTestCase {
    @MainActor
    func testImportReviewPresentationKeepsUncommittedMaterialUntilConfirmation() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousWindow = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        defer { window.isHidden = true; previousWindow?.makeKeyAndVisible() }

        for (name, style, textSize) in [
            ("light", UIUserInterfaceStyle.light, DynamicTypeSize.large),
            ("dark-large-text", UIUserInterfaceStyle.dark, DynamicTypeSize.accessibility3),
        ] {
            let persistence = InMemoryDemoReaderTrustSettingsPersistence()
            let controller = DemoReaderTrustSettingsController(persistence: persistence)
            await controller.awaitPendingOperations()
            let host = UIHostingController(rootView: NavigationView {
                ReaderTrustSettingsView(controller: controller)
            }.dynamicTypeSize(textSize))
            window.rootViewController = host
            window.overrideUserInterfaceStyle = style
            if #available(iOS 17.0, *) {
                window.traitOverrides.preferredContentSizeCategory = name == "light" ? .large : .accessibilityExtraExtraExtraLarge
            }
            window.makeKeyAndVisible()

            func capture(_ state: String) async throws {
                try await Task.sleep(nanoseconds: 500_000_000)
                window.layoutIfNeeded()
                let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                    XCTAssertTrue(window.drawHierarchy(in: window.bounds, afterScreenUpdates: true))
                }
                let attachment = XCTAttachment(image: image)
                attachment.name = "reader-trust-\(state)-\(name)"
                attachment.lifetime = .keepAlways
                add(attachment)
            }

            try await capture("empty")
            await controller.prepareImport(sourceName: "reader-ca.der", data: try XCTUnwrap(Data(base64Encoded: Self.testReaderCaDerBase64)))
            try await capture("review")
            // Presentation must not trigger the screen's departure cleanup and
            // silently discard the preview, or commit anything before approval.
            XCTAssertNotNil(host.presentedViewController)
            XCTAssertNotNil(controller.pendingImport)
            XCTAssertTrue(controller.settings.trustAnchors.isEmpty)
            XCTAssertNil(persistence.encodedSettings)

            controller.cancelImport()
            for _ in 0..<100 where host.presentedViewController != nil {
                try await Task.sleep(nanoseconds: 20_000_000)
            }
            try await capture("cancelled")
            XCTAssertNil(host.presentedViewController)
            XCTAssertTrue(controller.settings.trustAnchors.isEmpty)
            XCTAssertNil(persistence.encodedSettings)

            await controller.prepareImport(sourceName: "reader-ca.der", data: try XCTUnwrap(Data(base64Encoded: Self.testReaderCaDerBase64)))
            controller.confirmImport()
            await controller.awaitPendingOperations()
            try await capture("configured")
            XCTAssertEqual(controller.settings.trustAnchors.count, 1)

            await controller.prepareImport(sourceName: "invalid.der", data: Data("not a certificate".utf8))
            try await capture("error")
            XCTAssertNotNil(controller.errorMessage)
            XCTAssertEqual(controller.settings.trustAnchors.count, 1)
            controller.dismissError()
            for _ in 0..<100 where host.presentedViewController != nil {
                try await Task.sleep(nanoseconds: 20_000_000)
            }
            window.isHidden = true
        }
    }

    @MainActor
    func testPolicyPersistsWithCanonicalCodecAndLoadsInNewController() async throws {
        let persistence = InMemoryDemoReaderTrustSettingsPersistence()
        let first = DemoReaderTrustSettingsController(persistence: persistence)
        await first.awaitPendingOperations()

        first.setReaderPolicy(.requireTrusted)
        await first.awaitPendingOperations()

        let encoded = try XCTUnwrap(persistence.encodedSettings)
        XCTAssertTrue(encoded.contains("\"version\":1"))
        XCTAssertTrue(encoded.contains("\"readerPolicy\":\"require_trusted\""))
        let reloaded = DemoReaderTrustSettingsController(persistence: persistence)
        await reloaded.awaitPendingOperations()
        XCTAssertEqual(reloaded.settings.readerPolicy, .requireTrusted)
        XCTAssertNil(reloaded.errorMessage)
    }

    @MainActor
    func testInvalidStoredSettingsFailClosedToDefaultsWithVisibleError() async {
        let persistence = InMemoryDemoReaderTrustSettingsPersistence()
        persistence.encodedSettings = "{\"version\":99}"

        let controller = DemoReaderTrustSettingsController(persistence: persistence)
        await controller.awaitPendingOperations()

        XCTAssertEqual(controller.settings, ProximityReaderTrustSettings())
        XCTAssertNotNil(controller.errorMessage)
    }

    @MainActor
    func testInvalidFileImportProducesRecoverableErrorWithoutChangingSettings() async {
        let persistence = InMemoryDemoReaderTrustSettingsPersistence()
        let controller = DemoReaderTrustSettingsController(persistence: persistence)
        await controller.awaitPendingOperations()

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
        await controller.awaitPendingOperations()
        controller.setReaderPolicy(.requireTrusted)
        await controller.awaitPendingOperations()
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
        await controller.awaitPendingOperations()

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

    func testSwiftSettingsApplyReaderPolicyToSessionConfiguration() throws {
        let settings = ProximityReaderTrustSettings(readerPolicy: .requireTrusted)

        let configuration = try settings.applying()

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
