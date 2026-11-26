import Foundation

/// A wallet inventory row with display metadata and format-specific type hints.
public struct CredentialSummary: Identifiable, Equatable, Sendable {
    /// The identifier used to read or remove the stored credential.
    public let id: String
    /// The stored credential format identifier.
    public let format: String
    /// The issuer identifier, when available.
    public let issuer: String?
    /// The subject identifier, when available.
    public let subject: String?
    /// The display label stored with the credential, when available.
    public let label: String?
    /// The time the credential was added to the wallet, when available.
    public let addedAt: Date?
    /// JSON containing the format-specific type fields used to identify the card.
    public let typeMetadataJSON: String
    /// Optional issuer or wallet display metadata encoded as JSON.
    public let metadataJSON: String?

    init(id: String, format: String, issuer: String?, subject: String?, label: String?, addedAt: Date?,
         typeMetadataJSON: String, metadataJSON: String?) {
        self.id = id
        self.format = format
        self.issuer = issuer
        self.subject = subject
        self.label = label
        self.addedAt = addedAt
        self.typeMetadataJSON = typeMetadataJSON
        self.metadataJSON = metadataJSON
    }

    /// Projects an already-loaded credential for an inventory backed by a custom store.
    /// - Parameter credential: The full credential returned by the store.
    public init(_ credential: Credential) {
        id = credential.id
        format = credential.format
        issuer = credential.issuer
        subject = credential.subject
        label = credential.label
        addedAt = credential.addedAt
        metadataJSON = credential.metadataJSON
        let payload = (try? JSONSerialization.jsonObject(with: Data(credential.credentialDataJSON.utf8))) as? [String: Any] ?? [:]
        var types: [String: Any] = [:]
        for key in ["vct", "docType", "doctype", "doc_type"] { types[key] = payload[key] as? String }
        func typeValue(_ value: Any?) -> Any? {
            if let values = value as? [Any] { return values.compactMap { $0 as? String } }
            return value as? String
        }
        types["type"] = typeValue(payload["type"])
        if let nested = typeValue((payload["vc"] as? [String: Any])?["type"]) { types["vc"] = ["type": nested] }
        typeMetadataJSON = (try? JSONSerialization.data(withJSONObject: types, options: .sortedKeys)).map {
            String(decoding: $0, as: UTF8.self)
        } ?? "{}"
    }
}
