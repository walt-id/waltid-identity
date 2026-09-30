import Foundation
import Security
import XCTest
@testable import iosApp

final class DemoClientIdTrustTests: XCTestCase {
    func testDemoAnchorsParseAsDistinctCertificates() throws {
        let ders = try DemoClientIdTrust.x509TrustAnchorPems.map(der(fromPEM:))
        XCTAssertEqual(ders.count, 12)
        XCTAssertEqual(Set(ders).count, ders.count)
        XCTAssertEqual(
            DemoClientIdTrust.clientIDTrustConfiguration.x509TrustAnchorsPEM,
            DemoClientIdTrust.x509TrustAnchorPems
        )
    }

    private func der(fromPEM pem: String) throws -> Data {
        let base64 = pem
            .split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.hasPrefix("-----") }
            .joined()
        let der = try XCTUnwrap(Data(base64Encoded: base64), "PEM is not valid Base64")
        let certificate = try XCTUnwrap(
            SecCertificateCreateWithData(nil, der as CFData),
            "PEM is not a parseable X.509 certificate"
        )
        return SecCertificateCopyData(certificate) as Data
    }
}
