import Foundation

/// OAuth grant selected by the resolved credential offer.
public enum IssuanceGrant: Equatable, Sendable {
    /// Issuance using a pre-authorized code and optional transaction code.
    case preAuthorizedCode

    /// Issuance using browser authorization and an authorization code.
    case authorizationCode
}

/// Transaction-code input requested by a pre-authorized credential offer.
public struct IssuanceTransactionCode: Equatable, Sendable {
    /// Expected input mode, such as numeric or text.
    public let inputMode: String?

    /// Expected number of characters when specified by the issuer.
    public let length: Int?

    /// Optional user-facing instructions supplied by the issuer.
    public let descriptionText: String?

    /// Creates a transaction-code requirement.
    ///
    /// - Parameters:
    ///   - inputMode: Expected input mode.
    ///   - length: Expected number of characters.
    ///   - descriptionText: Optional user-facing instructions.
    public init(inputMode: String?, length: Int?, descriptionText: String?) {
        self.inputMode = inputMode
        self.length = length
        self.descriptionText = descriptionText
    }
}

/// Display-safe issuer information resolved from credential issuer metadata.
public struct IssuanceIssuerPreview: Equatable, Sendable {
    /// Credential issuer identifier.
    public let identifier: String

    /// Localized display metadata advertised by the issuer.
    public let display: MetadataDisplay

    /// Source and verification provenance for this metadata.
    public let metadataProvenance: MetadataProvenance

    /// Creates an issuer preview.
    /// - Parameters:
    ///   - identifier: Credential issuer identifier.
    ///   - metadataProvenance: Source and verification provenance of the issuer metadata.
    ///   - display: Localized issuer name and artwork supplied by the issuer.
    public init(identifier: String, metadataProvenance: MetadataProvenance, display: MetadataDisplay = .init()) {
        self.identifier = identifier
        self.metadataProvenance = metadataProvenance
        self.display = display
    }
}

/// Provenance for Credential Issuer Metadata used by the wallet.
public enum MetadataProvenance: Equatable, Sendable {
    /// Metadata was received as an unsigned JSON document.
    case unsigned
    /// Metadata was received in a JWS verified by the configured trust resolver.
    case signed(SignedMetadataProvenance)
}

/// Verified signed Credential Issuer Metadata provenance.
public struct SignedMetadataProvenance: Equatable, Sendable {
    /// Exact compact JWS returned by the issuer.
    public let compactJWT: String
    /// JWS algorithm verified by the configured trust resolver.
    public let algorithm: String
    /// Identifier of the trusted verification key, as reported by the trust resolver.
    public let keyID: String?
    /// Authority category established by the trust resolver.
    public let trustType: MetadataTrustType

    /// Creates signed metadata provenance.
    ///
    /// - Parameters:
    ///   - compactJWT: Exact compact JWS returned by the issuer.
    ///   - algorithm: JWS algorithm verified by the trust resolver.
    ///   - keyID: Identifier of the trusted verification key, as reported by the trust resolver.
    ///   - trustType: Authority category established by the trust resolver.
    public init(compactJWT: String, algorithm: String, keyID: String?, trustType: MetadataTrustType) {
        self.compactJWT = compactJWT
        self.algorithm = algorithm
        self.keyID = keyID
        self.trustType = trustType
    }
}

/// Display-safe credential configuration offered by an issuer.
public struct IssuanceCredentialPreview: Equatable, Sendable {
    /// Issuer-defined credential configuration identifier.
    public let configurationID: String

    /// OpenID4VCI credential format identifier.
    public let format: String

    /// Localized display metadata advertised by the issuer.
    public let display: MetadataDisplay

    /// SD-JWT VC type from the offered credential configuration, when present.
    public let vct: String?

    /// mdoc document type from the offered credential configuration, when present.
    public let doctype: String?

    /// Issuer display metadata in the same locale-preserving shape as a stored credential sidecar.
    /// Claim definitions are advertised metadata, not received values or proof of issuer trust.
    public let metadataJSON: String?

    /// Creates a preview of an offered credential.
    /// - Parameters:
    ///   - configurationID: Issuer-defined credential configuration identifier.
    ///   - format: OpenID4VCI credential format identifier.
    ///   - display: Localized name, description and artwork.
    ///   - vct: SD-JWT VC type advertised by this configuration.
    ///   - doctype: mdoc document type advertised by this configuration.
    ///   - metadataJSON: Issuer display metadata and claim definitions.
    public init(
        configurationID: String,
        format: String,
        display: MetadataDisplay = .init(),
        vct: String? = nil,
        doctype: String? = nil,
        metadataJSON: String? = nil
    ) {
        self.configurationID = configurationID
        self.format = format
        self.display = display
        self.vct = vct
        self.doctype = doctype
        self.metadataJSON = metadataJSON
    }

    /// Minimal payload so `CredentialTitles` can resolve a friendly type when display omits a name.
    public var typePayloadJSON: String? {
        var fields: [String] = []
        if let vct, !vct.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            fields.append(#""vct":\#(Self.jsonString(vct))"#)
        }
        if let doctype, !doctype.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            fields.append(#""docType":\#(Self.jsonString(doctype))"#)
        }
        guard !fields.isEmpty else { return nil }
        return "{\(fields.joined(separator: ","))}"
    }

    private static func jsonString(_ value: String) -> String {
        let escaped = value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
        return "\"\(escaped)\""
    }
}

/// Typed preview of a resolved OpenID4VCI credential offer.
public struct IssuanceOfferPreview: Equatable, Sendable {
    /// OAuth grant selected by the offer.
    public let grant: IssuanceGrant

    /// Issuer information suitable for review UI.
    public let issuer: IssuanceIssuerPreview

    /// Credential configurations included in the offer.
    public let credentials: [IssuanceCredentialPreview]

    /// Maximum copies per credential request advertised by the issuer; absence means one copy.
    public let batchSize: Int?

    /// Transaction-code requirement for pre-authorized issuance, if any.
    public let transactionCode: IssuanceTransactionCode?

    /// Creates an offer preview.
    ///
    /// - Parameters:
    ///   - grant: OAuth grant selected by the offer.
    ///   - issuer: Issuer information suitable for review UI.
    ///   - credentials: Credential configurations included in the offer.
    ///   - transactionCode: Optional transaction-code requirement.
    ///   - batchSize: Optional advertised copy limit; never an automatic requested count.
    public init(
        grant: IssuanceGrant,
        issuer: IssuanceIssuerPreview,
        credentials: [IssuanceCredentialPreview],
        transactionCode: IssuanceTransactionCode?,
        batchSize: Int? = nil
    ) {
        self.grant = grant
        self.issuer = issuer
        self.credentials = credentials
        self.transactionCode = transactionCode
        self.batchSize = batchSize
    }
}
