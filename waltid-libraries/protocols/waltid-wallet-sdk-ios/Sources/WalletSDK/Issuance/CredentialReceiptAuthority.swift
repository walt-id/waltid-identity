import Foundation

/// Host-provisioned ES256 signing authority for one protocol issuer and credential format.
public enum CredentialReceiptAuthority: Sendable {
    /// Issuer signing key and optional signed issuer identifier. Omission uses the credential issuer URL.
    case sdJwtVC(credentialIssuer: URL, publicJWK: String, signedIssuer: String? = nil)
    /// Trusted document signer key for mdoc receipt.
    case mdoc(credentialIssuer: URL, publicJWK: String)

    var fields: [String: String] {
        switch self {
        case let .sdJwtVC(origin, key, issuer):
            return ["type": "sd-jwt-vc", "credentialIssuer": origin.absoluteString, "publicJwk": key, "signedIssuer": issuer ?? origin.absoluteString]
        case let .mdoc(origin, key):
            return ["type": "mdoc", "credentialIssuer": origin.absoluteString, "publicJwk": key]
        }
    }
}
