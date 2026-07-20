import Foundation

struct VerifierClientIdentifier {
    let scheme: Scheme
    let value: String
    let rawValue: String

    enum Scheme: CaseIterable {
        case redirectURI
        case x509SanDNS
        case x509Hash
        case decentralizedIdentifier
        case legacyDID
        case verifierAttestation
        case openIDFederation
        case preRegistered
        case unsupported

        var prefix: String? {
            switch self {
            case .redirectURI: return "redirect_uri"
            case .x509SanDNS: return "x509_san_dns"
            case .x509Hash: return "x509_hash"
            case .decentralizedIdentifier: return "decentralized_identifier"
            case .legacyDID: return "did"
            case .verifierAttestation: return "verifier_attestation"
            case .openIDFederation: return "openid_federation"
            case .preRegistered, .unsupported: return nil
            }
        }

        var genericLabel: String? {
            switch self {
            case .x509Hash: return "X.509 verifier"
            case .decentralizedIdentifier, .legacyDID: return "DID verifier"
            case .verifierAttestation: return "Verifier attestation"
            case .openIDFederation: return "OpenID Federation verifier"
            case .redirectURI, .x509SanDNS, .preRegistered, .unsupported: return nil
            }
        }
    }

    static func parse(_ rawClientID: String) -> VerifierClientIdentifier? {
        let rawValue = rawClientID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !rawValue.isEmpty else { return nil }

        if NetworkOrigin.parse(rawValue) != nil {
            return VerifierClientIdentifier(scheme: .preRegistered, value: rawValue, rawValue: rawValue)
        }

        guard let prefixedValue = ClientIdentifierPrefix.split(rawValue) else {
            return VerifierClientIdentifier(scheme: .preRegistered, value: rawValue, rawValue: rawValue)
        }

        let scheme = Scheme.allCases.first { $0.prefix == prefixedValue.prefix } ?? .unsupported

        return VerifierClientIdentifier(scheme: scheme, value: prefixedValue.value, rawValue: rawValue)
    }
}

private struct ClientIdentifierPrefix {
    let prefix: String
    let value: String

    static func split(_ rawValue: String) -> ClientIdentifierPrefix? {
        guard let separator = rawValue.firstIndex(of: ":"),
              separator != rawValue.startIndex else {
            return nil
        }

        return ClientIdentifierPrefix(
            prefix: String(rawValue[..<separator]),
            value: String(rawValue[rawValue.index(after: separator)...])
        )
    }
}

struct NetworkOrigin {
    let host: String

    static func parse(_ rawValue: String) -> NetworkOrigin? {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty,
              let url = URL(string: value),
              let scheme = url.scheme?.lowercased(),
              httpSchemes.contains(scheme),
              let host = url.host,
              !host.isEmpty else {
            return nil
        }

        return NetworkOrigin(host: host)
    }

    private static let httpSchemes: Set<String> = ["http", "https"]
}
