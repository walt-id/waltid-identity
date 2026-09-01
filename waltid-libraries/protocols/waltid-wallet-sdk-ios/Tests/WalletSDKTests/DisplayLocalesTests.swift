import XCTest
@testable import WalletSDK

final class DisplayLocalesTests: XCTestCase {
    func testPrefersLanguageRegionThenLanguage() {
        let selected = DisplayLocales.select(
            [("en", "English"), ("de", "German"), ("de-AT", "Austrian")],
            preferredLocales: ["de-AT"],
            localeOf: { $0.0 }
        )
        XCTAssertEqual(selected?.1, "Austrian")
    }

    func testFallsBackToUntaggedThenFirst() {
        XCTAssertEqual(
            DisplayLocales.select(
                [("en", "English"), (nil, "untagged")],
                preferredLocales: ["fr"],
                localeOf: { $0.0 }
            )?.1,
            "untagged"
        )
        XCTAssertEqual(
            DisplayLocales.select(
                [("en", "English"), ("de", "German")],
                preferredLocales: [],
                localeOf: { $0.0 }
            )?.1,
            "English"
        )
    }

    func testNormalizesUnderscoreAndCase() {
        XCTAssertEqual(DisplayLocales.normalize("de_AT"), "de-at")
        XCTAssertEqual(DisplayLocales.lookupTags("de-at"), ["de-at", "de"])
    }
}
