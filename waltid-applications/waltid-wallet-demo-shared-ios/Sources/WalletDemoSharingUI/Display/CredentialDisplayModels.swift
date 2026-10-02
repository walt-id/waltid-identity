import Foundation
import WalletSDK

public struct CredentialDetails: Equatable, Identifiable {
    public let id: String
    public let title: String
    public let issuer: String?
    public let subject: String?
    public let format: String
    public let addedAt: Date?
    public let groups: [ClaimGroup]
    public let issuerDisplay: MetadataDisplay?
    public let credentialDisplay: MetadataDisplay?
    public let credentialDataJSON: String?
    let cardTitle: String

    public init(
        id: String,
        title: String,
        issuer: String?,
        subject: String?,
        format: String,
        addedAt: Date?,
        groups: [ClaimGroup],
        metadataJSON: String? = nil,
        issuerDisplay: MetadataDisplay? = nil,
        credentialDisplay: MetadataDisplay? = nil,
        credentialDataJSON: String? = nil,
        preferredLocales: [String] = Locale.preferredLanguages
    ) {
        self.id = id
        self.title = title
        self.issuer = issuer
        self.subject = subject
        self.format = format
        self.addedAt = addedAt
        self.groups = applyClaimMetadata(groups, metadata: StoredCredentialMetadataParser.claims(from: metadataJSON, preferredLocales: preferredLocales), format: format)
        self.issuerDisplay = issuerDisplay
            ?? StoredCredentialMetadataParser.issuerDisplay(
                from: metadataJSON,
                preferredLocales: preferredLocales
            )
        self.credentialDisplay = credentialDisplay
            ?? StoredCredentialMetadataParser.credentialDisplay(
                from: metadataJSON,
                preferredLocales: preferredLocales
            )
        self.credentialDataJSON = credentialDataJSON
        self.cardTitle = CredentialTitles.displayName(
            format: format,
            credentialDataJSON: credentialDataJSON,
            displayName: self.credentialDisplay?.name,
            fallback: title
        )
    }
}

public struct ClaimGroup: Equatable, Identifiable {
    /// Stable semantic identity, independent of localized headings.
    public let id: String
    public let title: String
    public let items: [ClaimItem]
    public let initiallyExpanded: Bool
    public let transactionType: String?

    public init(id: String, title: String, items: [ClaimItem], initiallyExpanded: Bool = true, transactionType: String? = nil) {
        self.id = id
        self.title = title
        self.items = items
        self.initiallyExpanded = initiallyExpanded
        self.transactionType = transactionType
    }

}

public enum ClaimLabelSource: Equatable { case wallet, issuerMetadata, request }

public struct ClaimItem: Equatable, Identifiable {
    public let path: ClaimItemPath
    public let pathComponents: [String]
    public let label: String
    public let value: DisplayValue
    public let rawValue: String?
    public let roles: Set<ClaimRole>
    public let labelSource: ClaimLabelSource
    public let displayOrder: Int?

    public var id: String { path.id }

    public init(
        path: ClaimItemPath,
        pathComponents: [String] = [],
        label: String,
        value: DisplayValue,
        rawValue: String?,
        roles: Set<ClaimRole> = [],
        labelSource: ClaimLabelSource = .wallet,
        displayOrder: Int? = nil
    ) {
        self.path = path
        self.pathComponents = pathComponents
        self.label = label
        self.value = value
        self.rawValue = rawValue
        self.roles = roles
        self.labelSource = labelSource
        self.displayOrder = displayOrder
    }
}

public enum DisplayValue: Equatable {
    case text(String)
    case number(String)
    case bool(Bool)
    case object([ClaimItem])
    case list([DisplayValue])
    case deferredImage(DeferredCredentialImage)
    case image(encoded: String, data: Data, mimeType: String, byteCount: Int)
    case decodedText(String)
    case raw(String)
    case null
}

public struct ClaimItemPath: Hashable {
    private let renderedID: RenderedClaimPath

    public var id: String {
        renderedID.value
    }

    public init(id: String) {
        self.renderedID = .raw(id)
    }

    private init(renderedID: RenderedClaimPath) {
        self.renderedID = renderedID
    }

    public func indexedChild(_ index: Int) -> ClaimItemPath {
        ClaimItemPath(renderedID: renderedID.indexed(index))
    }

    public func child(_ name: String) -> ClaimItemPath {
        ClaimItemPath(renderedID: renderedID.child(name))
    }

    public static func root() -> ClaimItemPath {
        ClaimItemPath(renderedID: .raw(DisplayClaimPathRoot.root.id))
    }

    public static func topLevel(_ name: String) -> ClaimItemPath {
        ClaimItemPath(renderedID: .raw(claimPathKey(name)))
    }

    public static func transactionData(index: Int, field: DisplayTransactionDataField) -> ClaimItemPath {
        ClaimItemPath(
            renderedID: RenderedClaimPath
                .raw(DisplayClaimPathRoot.transactionData.id)
                .indexed(index)
                .child(field.id)
        )
    }
}

private struct RenderedClaimPath: Hashable {
    private enum Operation: Hashable {
        case child(String)
        case index(Int)
    }

    private let root: String
    private let operations: [Operation]

    var value: String {
        operations.reduce(root) { partial, operation in
            switch operation {
            case .child(let name):
                let key = claimPathKey(name)
                return partial + (key.hasPrefix("[") ? "" : ".") + key
            case .index(let index): return "\(partial)[\(index)]"
            }
        }
    }

    func child(_ name: String) -> RenderedClaimPath {
        RenderedClaimPath(root: root, operations: operations + [.child(name)])
    }

    func indexed(_ index: Int) -> RenderedClaimPath {
        RenderedClaimPath(root: root, operations: operations + [.index(index)])
    }

    static func raw(_ value: String) -> RenderedClaimPath {
        RenderedClaimPath(root: value, operations: [])
    }
}

public enum ClaimGroupKind: CaseIterable {
    case personal
    case ageAttestations
    case address
    case other
    case travelDocumentData
    case technical

    public var id: String {
        switch self {
        case .personal: return "personal"
        case .ageAttestations: return "age"
        case .address: return "address"
        case .other: return "data"
        case .travelDocumentData: return "travel"
        case .technical: return "technical"
        }
    }

    public var title: String {
        switch self {
        case .personal: return "Personal details"
        case .ageAttestations: return "Age attestations"
        case .address: return "Address"
        case .other: return "Credential data"
        case .travelDocumentData: return "Travel document data"
        case .technical: return "Credential metadata"
        }
    }

    public var order: Int {
        switch self {
        case .personal: return 0
        case .ageAttestations: return 1
        case .address: return 2
        case .other: return 3
        case .travelDocumentData: return 4
        case .technical: return 5
        }
    }

    public var initiallyExpanded: Bool {
        switch self {
        case .ageAttestations, .travelDocumentData, .technical: return false
        case .personal, .address, .other: return true
        }
    }
}

public enum ClaimRole: Hashable {
    case givenName
    case familyName
    case temporal
    case expiryDate
    case image
    case credentialType
}

public enum CredentialDisplayText {
    public static let unknown = "Unknown"
    static let imageUnavailable = "Image unavailable or unsupported"

    public static func expires(_ date: String) -> String { "Expires \(date)" }
    public static func added(_ date: String) -> String { "Added \(date)" }
}

public struct DisplayClaimPath {
    public let itemPath: ClaimItemPath
    public let components: [String]

    public static func topLevel(_ name: String) -> DisplayClaimPath {
        DisplayClaimPath(itemPath: ClaimItemPath.topLevel(name), components: [name])
    }

    public static func transactionData(index: Int, field: DisplayTransactionDataField) -> DisplayClaimPath {
        DisplayClaimPath(
            itemPath: ClaimItemPath.transactionData(index: index, field: field),
            components: [DisplayClaimPathRoot.transactionData.id, field.id]
        )
    }

    public func child(_ child: String) -> DisplayClaimPath {
        DisplayClaimPath(
            itemPath: itemPath.child(child),
            components: components + [child]
        )
    }

    public func indexed(_ index: Int) -> DisplayClaimPath {
        DisplayClaimPath(itemPath: itemPath.indexedChild(index), components: components)
    }
}

public enum DisplayClaimPathRoot {
    case root
    case transactionData

    public var id: String {
        switch self {
        case .root: return "$"
        case .transactionData: return "transactionData"
        }
    }
}

public enum DisplayTransactionDataField {
    case type
    case credentialQueryIDs
    case details
    case raw

    public var id: String {
        switch self {
        case .type: return "type"
        case .credentialQueryIDs: return "credentialQueryIds"
        case .details: return "details"
        case .raw: return "raw"
        }
    }
}

/// Keeps encoded media out of list construction and view updates. The visible row owns the result.
public final class DeferredCredentialImage: Equatable {
    public let byteCount: Int?
    private let decode: () -> DisplayValue

    init(byteCount: Int? = nil, decode: @escaping () -> DisplayValue) {
        self.byteCount = byteCount
        self.decode = decode
    }

    public func resolve() -> DisplayValue { decode() }

    public static func == (lhs: DeferredCredentialImage, rhs: DeferredCredentialImage) -> Bool {
        lhs === rhs
    }
}

private func claimPathKey(_ name: String) -> String {
    if name.range(of: "^[A-Za-z_][A-Za-z0-9_]*$", options: .regularExpression) != nil { return name }
    // A JSON bracket segment preserves punctuation, namespace boundaries and numeric keys.
    let encoded = try! JSONSerialization.data(withJSONObject: [name], options: .withoutEscapingSlashes)
    return String(decoding: encoded, as: UTF8.self)
}
