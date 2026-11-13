@testable import WalletDemoSharingUI
import XCTest

final class CredentialCardArtOverlayTests: XCTestCase {
    func testBadgeClearsMeasuredIssuerChipsAndTheUnchangedStackPeek() {
        for width in [CGFloat(280), 320, 353, 360] {
            let top = credentialStatusBadgeTop(width)
            for chipBottom in [CGFloat(97), 109] {
                XCTAssertGreaterThan(top - chipBottom * width / 1280, 1.5)
            }
            XCTAssertGreaterThan(56 - top - 20, 2.5)
        }
    }

    func testThumbnailUsesMetadataBackgroundInsteadOfArtwork() {
        let art = "https://issuer.example/art.png"
        XCTAssertNil(credentialCardMetadataArtURI(backgroundColor: "#123456", backgroundImageURI: art, compact: true))
        XCTAssertEqual(credentialCardMetadataArtURI(backgroundColor: "#123456", backgroundImageURI: art, compact: false), art)
    }

    func testThumbnailUsesArtworkOnlyWhenUsableBackgroundIsUnavailable() {
        let art = "https://issuer.example/art.png"
        XCTAssertEqual(credentialCardMetadataArtURI(backgroundColor: nil, backgroundImageURI: art, compact: true), art)
        XCTAssertEqual(credentialCardMetadataArtURI(backgroundColor: "invalid", backgroundImageURI: art, compact: true), art)
        XCTAssertNil(credentialCardMetadataArtURI(backgroundColor: nil, backgroundImageURI: "http://issuer.example/art.png", compact: true))
    }

    func testCredentialLogoUsesHTTPSMetadataAndOtherwiseFallsBack() throws {
        XCTAssertEqual(
            credentialCardLogoSource("https://issuer.example/credential.png"),
            .metadata(try XCTUnwrap(URL(string: "https://issuer.example/credential.png")))
        )
        XCTAssertEqual(credentialCardLogoSource("http://issuer.example/logo.png"), .bundledWalt)
        XCTAssertEqual(credentialCardLogoSource(nil), .bundledWalt)
    }

    func testPendingMetadataArtDoesNotShowConstructedFallback() {
        XCTAssertFalse(
            showsConstructedCardArtOverlay(
                backgroundImageURI: "https://issuer.example/pid-bg.png",
                hasLoadedMetadataArt: false,
                metadataArtFailed: false
            )
        )
        XCTAssertFalse(
            showsConstructedCardArtOverlay(
                backgroundImageURI: "https://issuer.example/pid-bg.png",
                hasLoadedMetadataArt: true,
                metadataArtFailed: false
            )
        )
    }

    func testConstructedFallbackShowsWhenArtIsMissingOrRejected() {
        XCTAssertTrue(
            showsConstructedCardArtOverlay(
                backgroundImageURI: nil,
                hasLoadedMetadataArt: false,
                metadataArtFailed: false
            )
        )
        XCTAssertTrue(
            showsConstructedCardArtOverlay(
                backgroundImageURI: "https://issuer.example/pid-bg.png",
                hasLoadedMetadataArt: false,
                metadataArtFailed: true
            )
        )
    }
}
