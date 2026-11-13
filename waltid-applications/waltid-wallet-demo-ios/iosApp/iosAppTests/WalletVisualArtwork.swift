import Foundation
import UIKit
import WalletDemoSharingUI
import XCTest

/// Shared fixture input; status overlays are synthetic and do not describe issued PID/EMVCo cards.
struct WalletVisualArtwork: Decodable {
    struct Card: Decodable {
        let id: String
        let title: String
        let image: String?
        let background: String
        let status: String
    }
    struct Artwork: Decodable { let file: String }
    let schemaVersion: Int
    let cards: [Card]
    let artwork: [Artwork]
    static let origin = "https://wallet-visual.example/artwork/"

    static func load(from directory: URL) throws -> Self {
        let value = try JSONDecoder().decode(Self.self, from: Data(contentsOf: directory.appendingPathComponent("credential-artwork.json")))
        XCTAssertEqual(value.schemaVersion, 1)
        return value
    }

    var cardItems: [CredentialCardItem] {
        cards.map { card in
            CredentialCardItem(id: card.id, summary: CredentialCardSummary(title: card.title, issuer: "Layout fixture",
                backgroundColor: card.background, backgroundImageURI: card.image.map { Self.origin + $0 }, textColor: "#FFFFFF"))
        }
    }

    func images(from directory: URL) throws -> [String: Data] {
        try Dictionary(uniqueKeysWithValues: artwork.map { (Self.origin + $0.file, try Data(contentsOf: directory.appendingPathComponent($0.file))) })
    }

    /// Require decoded artworks and physical-right issue marks in the actual rendered stack.
    static func hasRenderedArtworksAndRightBadges(_ image: UIImage) -> Bool {
        guard let cg = image.cgImage else { return false }
        let width = cg.width, height = cg.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        return pixels.withUnsafeMutableBytes { buffer in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.draw(cg, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
            let bytes = buffer.bindMemory(to: UInt8.self)
            var blue = 0, orange = 0, navy = 0, redLeft = 0, redRight = 0
            for y in stride(from: 0, to: height, by: 4) {
                for x in stride(from: 0, to: width, by: 4) {
                    let i = (y * width + x) * 4
                    let r = bytes[i], g = bytes[i + 1], b = bytes[i + 2]
                    if r < 70 && g > 100 && b > 160 { blue += 1 }
                    if r > 100 && g > 50 && g < 110 && b < 80 { orange += 1 }
                    if r > 20 && r < 60 && g > 40 && g < 90 && b > 60 && b < 140 { navy += 1 }
                    if r > 180 && g < 120 && b < 130 {
                        if x < width / 2 { redLeft += 1 } else { redRight += 1 }
                    }
                }
            }
            return blue > 1_500 && orange > 500 && navy > 500 && redLeft == 0 && redRight > 30
        }
    }
}

/// The production HTTPS loader still decodes and validates bytes; only fixture I/O is intercepted.
final class WalletVisualArtworkProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    private nonisolated(unsafe) static var images: [String: Data] = [:]
    private nonisolated(unsafe) static var served: Set<String> = []

    static func install(_ images: [String: Data]) {
        lock.lock(); self.images = images; served = []; lock.unlock()
        XCTAssertTrue(URLProtocol.registerClass(self))
    }
    static func uninstall() {
        URLProtocol.unregisterClass(self)
        lock.lock(); images = [:]; served = []; lock.unlock()
    }
    static var ready: Bool {
        lock.lock(); defer { lock.unlock() }
        return !images.isEmpty && Set(images.keys).isSubset(of: served)
    }
    override class func canInit(with request: URLRequest) -> Bool {
        lock.lock(); defer { lock.unlock() }
        return request.url.map { images[$0.absoluteString] != nil } == true
    }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let url = request.url else { client?.urlProtocol(self, didFailWithError: URLError(.badURL)); return }
        Self.lock.lock(); let bytes = Self.images[url.absoluteString]; Self.lock.unlock()
        guard let bytes, let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "image/png", "Content-Length": String(bytes.count), "Cache-Control": "no-store"]) else {
            client?.urlProtocol(self, didFailWithError: URLError(.cancelled)); return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: bytes)
        client?.urlProtocolDidFinishLoading(self)
        Self.lock.lock(); Self.served.insert(url.absoluteString); Self.lock.unlock()
    }
    override func stopLoading() {}
}
