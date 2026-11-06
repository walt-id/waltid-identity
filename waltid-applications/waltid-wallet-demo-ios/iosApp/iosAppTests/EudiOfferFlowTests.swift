import XCTest
@testable import TestHelpers

final class EudiOfferFlowTests: XCTestCase {
    private let flow = EudiOfferFlow(client: WalletE2EClient())

    func testRedirectPreservesOptionalSignedPayload() throws {
        let payload = #"<input name="payload" value='{}'>"#
        XCTAssertEqual(try flow.redirectFields(page: payload), ["payload": "{}"])
        XCTAssertEqual(
            try flow.redirectFields(page: payload + #"<input name="payload_jwt" value="opaque.signed.value" />"#),
            ["payload": "{}", "payload_jwt": "opaque.signed.value"]
        )
    }

    func testPayloadFieldSupportsEitherHtmlQuoteStyle() throws {
        let pages = [
            #"<input type="hidden" name="payload" value='{"message":"A &amp; B"}'>"#,
            #"<input type="hidden" name="payload" value="{&quot;message&quot;:&quot;A &amp; B&quot;}" />"#,
            #"<input type="hidden" name="payload" value="{&#34;message&#34;:&#34;A &amp; B&#34;}" />"#,
        ]
        for page in pages {
            XCTAssertEqual(try flow.extractPayload(page: page), #"{"message":"A & B"}"#)
        }
    }

    func testPayloadEntitiesAreDecodedOnlyOnce() throws {
        let page = #"<input name="payload" value="{&#34;message&#34;:&#34;&amp;quot; &amp;lt;&#34;}" />"#
        XCTAssertEqual(try flow.extractPayload(page: page), #"{"message":"&quot; &lt;"}"#)
    }

    func testPayloadFieldRejectsMissingOrMismatchedQuotes() {
        for page in [
            #"<input name="payload_jwt" value="ignored">"#,
            #"<input name="payload" value='unterminated">"#,
        ] {
            XCTAssertThrowsError(try flow.extractPayload(page: page))
        }
    }
}
