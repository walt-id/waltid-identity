import WalletDemoSharingUI
import XCTest

final class CssColorParserTests: XCTestCase {
    func testParsesCssColorLevel3Forms() {
        XCTAssertEqual(CssColorParser.parse("#12107c"), CssColorChannels(red: 18 / 255, green: 16 / 255, blue: 124 / 255, alpha: 1))
        XCTAssertEqual(CssColorParser.parse("#123"), CssColorChannels(red: 17 / 255, green: 34 / 255, blue: 51 / 255, alpha: 1))
        XCTAssertEqual(CssColorParser.parse("rgb(255, 0, 128)"), CssColorChannels(red: 1, green: 0, blue: 128 / 255, alpha: 1))
        XCTAssertEqual(CssColorParser.parse("transparent")?.alpha, 0)
    }

    func testRejectsNonCss3Tokens() {
        XCTAssertNil(CssColorParser.parse("#11223344"))
        XCTAssertNil(CssColorParser.parse("12107c"))
        XCTAssertNil(CssColorParser.parse("rgbfoo(255, 0, 0)"))
        XCTAssertNil(CssColorParser.parse("rgb(255, 0, 0) extra"))
        XCTAssertNil(CssColorParser.parse("rgb(NaN, 0, 0)"))
        XCTAssertNil(CssColorParser.parse("rgb(0%, Infinity, 0%)"))
        XCTAssertNil(CssColorParser.parse("rgba(0, 0, 0, -Infinity)"))
        XCTAssertNil(CssColorParser.parse("hsl(Infinity, 100%, 50%)"))
        XCTAssertNil(CssColorParser.parse("hsl(0, NaN%, 50%)"))
        XCTAssertNil(CssColorParser.parse("blue"))
        XCTAssertNil(CssColorParser.parse(""))
        XCTAssertNil(CssColorParser.parse(nil))
    }
}
