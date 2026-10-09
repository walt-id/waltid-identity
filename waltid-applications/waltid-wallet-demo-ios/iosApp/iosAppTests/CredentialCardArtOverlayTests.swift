import WalletDemoSharingUI
import XCTest

final class CredentialCardArtOverlayTests: XCTestCase {
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
