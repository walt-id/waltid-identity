import Foundation
import XCTest
import WalletSDK

final class ProximityCRLBridgeTests: XCTestCase {
    func testConfiguredScopeAndFoundationTransportReachSharedVerifier() async throws {
        for (der, scope, expected) in [
            (ProximityCRLFixtures.good, ProximityCRLScope.readerCertificateAndIssuingAuthorities,
             ProximityCertificateRevocationResult.good),
            (ProximityCRLFixtures.readerRevoked, .readerCertificateAndIssuingAuthorities,
             .revoked(reason: "Reader certificate is revoked")),
            (ProximityCRLFixtures.authorityRevoked, .readerCertificateAndIssuingAuthorities,
             .revoked(reason: "Reader certificate authority is revoked")),
            (ProximityCRLFixtures.authorityRevoked, .readerCertificate, .good),
        ] {
            let fetcher = RecordingCRLFetcher(.available(der: der))
            let evaluator = try makeEvaluator(fetcher: fetcher, scope: scope)
            let result = try await evaluator.evaluate(evidence)
            XCTAssertEqual(result, expected)
            let requests = await fetcher.requests
            XCTAssertEqual(requests, [.init(url: "https://crl.example.test/swift.crl", maximumBytes: 2_097_152)])
        }
    }

    func testUnavailableMalformedOversizedAndUnauthenticResponsesRemainIndeterminate() async throws {
        var badSignature = ProximityCRLFixtures.good
        badSignature[badSignature.count - 1] ^= 1
        for response in [ProximityCRLFetchResult.unavailable, .available(der: Data([0x30, 0])),
                         .available(der: Data(count: 2_097_153)), .available(der: badSignature)] {
            let evaluator = try makeEvaluator(fetcher: RecordingCRLFetcher(response))
            let result = try await evaluator.evaluate(evidence)
            XCTAssertEqual(result, .indeterminate(reason: "Reader certificate CRL status could not be established"))
        }
    }

    func testInvalidIssuerInputBecomesASwiftWalletError() throws {
        for certificates in [[Data](), [Data()], [Data([0x30, 0])], [Data(count: 65_537)],
                             Array(repeating: ProximityCRLFixtures.issuer, count: 11)] {
            XCTAssertThrowsError(try ProximityCRLRevocationEvaluator(
                issuerCertificatesDER: certificates, scope: .readerCertificate,
                fetcher: RecordingCRLFetcher(.unavailable)
            )) { error in
                guard case WalletError.invalidInput = error else {
                    return XCTFail("Expected a Swift input error, received \(type(of: error))")
                }
            }
        }
    }

    func testCancellationSurvivesTheAsynchronousFetcherBridge() async throws {
        let fetcher = SuspendedCRLFetcher()
        let evaluator = try makeEvaluator(fetcher: fetcher)
        let evidence = evidence
        let evaluation = Task { try await evaluator.evaluate(evidence) }
        await fetcher.waitUntilRequested()
        evaluation.cancel()
        do {
            _ = try await evaluation.value
            XCTFail("Cancelled CRL evaluation returned a status")
        } catch is CancellationError {
            // Swift callers retain ordinary task cancellation semantics.
        }
    }

    private var evidence: ProximityReaderEvidence {
        .init(scope: .wholeRequest, certificateChainDER: [ProximityCRLFixtures.reader])
    }

    private func makeEvaluator(
        fetcher: any ProximityCRLFetcher,
        scope: ProximityCRLScope = .readerCertificateAndIssuingAuthorities
    ) throws -> ProximityCRLRevocationEvaluator {
        try .init(issuerCertificatesDER: [ProximityCRLFixtures.issuer], scope: scope, fetcher: fetcher)
    }
}

private actor RecordingCRLFetcher: ProximityCRLFetcher {
    struct Request: Equatable, Sendable {
        let url: String
        let maximumBytes: Int
    }
    private let response: ProximityCRLFetchResult
    private(set) var requests: [Request] = []

    init(_ response: ProximityCRLFetchResult) { self.response = response }

    func fetch(from url: URL, maximumBytes: Int) async throws -> ProximityCRLFetchResult {
        requests.append(.init(url: url.absoluteString, maximumBytes: maximumBytes))
        return response
    }
}

private actor SuspendedCRLFetcher: ProximityCRLFetcher {
    private var requested = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func fetch(from url: URL, maximumBytes: Int) async throws -> ProximityCRLFetchResult {
        requested = true
        waiters.forEach { $0.resume() }
        waiters.removeAll()
        try await Task.sleep(nanoseconds: 30_000_000_000)
        return .unavailable
    }

    func waitUntilRequested() async {
        if !requested { await withCheckedContinuation { waiters.append($0) } }
    }
}
