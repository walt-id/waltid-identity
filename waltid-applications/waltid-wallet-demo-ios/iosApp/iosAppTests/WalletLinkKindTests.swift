import Foundation
import WalletDemoSharingUI
import XCTest

final class WalletLinkKindTests: XCTestCase {
    func testRoutesTheSameSyntheticLinksAsCompose() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        let data = try Data(contentsOf: root.appendingPathComponent("waltid-wallet-demo-test-fixtures/resources/files/wallet-link-routing.json"))
        struct Case: Decodable { let input: String; let kind: String }
        let expected: [String: WalletLinkKind] = [
            "Empty": .empty, "Offer": .offer, "Presentation": .presentation, "Web": .web,
            "AuthorizationCallback": .authorizationCallback, "FidoHybrid": .fidoHybrid, "Unsupported": .unsupported
        ]
        for (index, fixture) in try JSONDecoder().decode([Case].self, from: data).enumerated() {
            XCTAssertEqual(WalletLinkKind.classify(fixture.input), expected[fixture.kind], "Routing fixture \(index)")
        }
    }
}

final class WalletLinkResolverTests: XCTestCase {
    func testRoutesTheSameDocumentsAsCompose() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        let data = try Data(contentsOf: root.appendingPathComponent("waltid-wallet-demo-test-fixtures/resources/files/wallet-link-documents.json"))
        struct Case: Decodable { let name: String; let body: String; let kind: String; let parameter: String? }
        let source = "https://example.test/link"
        for fixture in try JSONDecoder().decode([Case].self, from: data) {
            if fixture.kind == "Unsupported" {
                XCTAssertThrowsError(try WalletLinkResolver.routeDocument(url: source, body: fixture.body), fixture.name)
            } else {
                let result = try WalletLinkResolver.routeDocument(url: source, body: fixture.body)
                XCTAssertEqual(result.kind, fixture.kind == "Offer" ? .offer : .presentation, fixture.name)
                let parameter = try XCTUnwrap(fixture.parameter)
                let value = URLComponents(string: result.url)?.queryItems?.first(where: { $0.name == parameter })?.value
                XCTAssertEqual(value, parameter == "request_uri" ? source : fixture.body, fixture.name)
            }
        }
    }

    func testFollowsRedirectsWithoutGuessingFromPathOrTryingBothProtocols() async throws {
        var fetched = [String]()
        let result = try await WalletLinkResolver.resolve("https://example.test/share") { url in
            fetched.append(url)
            return WalletLinkDocument(status: 302, location: fetched.count == 1
                ? "/receive" : "openid4vp://?client_id=demo&dcql_query=%7B%7D")
        }
        XCTAssertEqual(fetched, ["https://example.test/share", "https://example.test/receive"])
        XCTAssertEqual(result.kind, .presentation)
    }

    func testRejectsRedirectLoopsDowngradesOversizeAndErrors() async {
        for document in [WalletLinkDocument(status: 302, location: "/link"),
            WalletLinkDocument(status: 302, location: "http://example.test/link"), WalletLinkDocument(status: 503)] {
            do { _ = try await WalletLinkResolver.resolve("https://example.test/link") { _ in document }; XCTFail("Expected failure") }
            catch { XCTAssertTrue(error is WalletLinkError) }
        }
        XCTAssertThrowsError(try WalletLinkResolver.routeDocument(url: "https://example.test/link", body: String(repeating: "x", count: 262_145)))
        do { _ = try await WalletLinkResolver.resolve("https://example.test/link") { _ in throw CancellationError() }; XCTFail("Expected cancellation") }
        catch { XCTAssertTrue(error is CancellationError) }
    }
}
