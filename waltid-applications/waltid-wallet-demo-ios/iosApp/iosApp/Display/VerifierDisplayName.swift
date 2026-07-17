import Foundation

enum VerifierDisplayName {
    static func value(verifierName: String?, clientID: String?, responseURI: URL?) -> String {
        if let verifierName = verifierName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !verifierName.isEmpty {
            return verifierName
        }

        if let clientIdentifier = clientID.flatMap(VerifierClientIdentifier.parse) {
            if let host = clientIdentifier.host {
                return host
            }
            if let displayName = clientIdentifier.displayName {
                return displayName
            }
        }

        if let host = responseURI?.host, !host.isEmpty {
            return host
        }

        return "Unknown verifier"
    }

}

private extension VerifierClientIdentifier {
    var host: String? {
        switch scheme {
        case .redirectURI, .preRegistered, .openIDFederation:
            return NetworkOrigin.parse(value)?.host
        case .x509SanDNS, .x509Hash, .decentralizedIdentifier, .legacyDID, .verifierAttestation, .unsupported:
            return nil
        }
    }

    var displayName: String? {
        if let genericLabel = scheme.genericLabel {
            return genericLabel
        }
        if scheme == .x509SanDNS {
            return value.isEmpty ? nil : value
        }
        if scheme == .preRegistered, rawValue.count <= 48 {
            return rawValue
        }
        return nil
    }
}
