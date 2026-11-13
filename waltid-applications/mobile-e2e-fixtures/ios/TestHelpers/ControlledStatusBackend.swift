import Foundation

/// Local control of the isolated signed-credential issuer; never a wallet display override.
public final class ControlledStatusBackend {
    public struct Offer {
        public let url: String
        fileprivate let configuration: String
        fileprivate let priorIDs: Set<String>
    }
    public struct Reference {
        public let id: String
        public let configuration: String
        public let purpose: String
        public let index: Int
    }

    private let control: URL
    private let client = WalletE2EClient()

    public init(controlURL: URL) throws {
        guard controlURL.scheme == "http", ["localhost", "127.0.0.1", "::1"].contains(controlURL.host),
              controlURL.user == nil, controlURL.password == nil, controlURL.query == nil, controlURL.fragment == nil else {
            throw Self.invalid("The controlled issuer requires a loopback control URL.")
        }
        control = controlURL
    }

    public func createOffer(configuration: String) async throws -> Offer {
        let prior = Set(try await issued().map(\.id))
        let response = try await post("offers", ["configuration": configuration])
        guard let url = response["credentialOffer"] as? String else { throw Self.invalid("The fixture returned no offer.") }
        return Offer(url: url, configuration: configuration, priorIDs: prior)
    }

    public func issuedReference(for offer: Offer) async throws -> Reference {
        let matches = try await issued().filter { $0.configuration == offer.configuration && !offer.priorIDs.contains($0.id) }
        guard matches.count == 1, let reference = matches.first else {
            throw Self.invalid("Expected exactly one new credential for this fixture offer.")
        }
        return reference
    }

    public func setStatus(_ reference: Reference, value: Int) async throws {
        _ = try await post("status", ["purpose": reference.purpose, "index": reference.index, "value": value])
    }

    public func setOutage(_ enabled: Bool) async throws { _ = try await post("outage", ["enabled": enabled]) }

    private func issued() async throws -> [Reference] {
        let response = try await client.jsonRequest(url: control.appendingPathComponent("state"))
        guard let values = response["issued"] as? [[String: Any]] else { throw Self.invalid("Invalid fixture inventory.") }
        return try values.map {
            guard let id = $0["id"] as? String, let configuration = $0["configuration"] as? String,
                  let purpose = $0["purpose"] as? String, let index = $0["index"] as? Int else {
                throw Self.invalid("Invalid fixture reference.")
            }
            return Reference(id: id, configuration: configuration, purpose: purpose, index: index)
        }
    }

    private func post(_ path: String, _ body: [String: Any]) async throws -> [String: Any] {
        try await client.jsonRequest(url: control.appendingPathComponent(path), method: "POST",
            headers: ["Content-Type": "application/json"], body: JSONSerialization.data(withJSONObject: body))
    }

    private static func invalid(_ message: String) -> NSError {
        NSError(domain: "ControlledStatusFixture", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }
}
