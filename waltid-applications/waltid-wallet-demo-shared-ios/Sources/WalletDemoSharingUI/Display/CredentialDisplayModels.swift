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
    public let metadataJSON: String?
    public let issuerDisplay: MetadataDisplay?
    /// Wallet-local store identifier used for deletion. Differs from ``id`` for presentation options.
    public let credentialId: String

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
        credentialId: String? = nil
    ) {
        self.id = id
        self.title = title
        self.issuer = issuer
        self.subject = subject
        self.format = format
        self.addedAt = addedAt
        self.groups = groups
        self.metadataJSON = metadataJSON
        self.issuerDisplay = issuerDisplay
            ?? StoredCredentialMetadataParser.issuerDisplay(from: metadataJSON)
        self.credentialId = credentialId ?? id
    }
}

public struct ClaimGroup: Equatable, Identifiable {
    public let title: String
    public let items: [ClaimItem]
    public let initiallyExpanded: Bool

    public init(title: String, items: [ClaimItem], initiallyExpanded: Bool = true) {
        self.title = title
        self.items = items
        self.initiallyExpanded = initiallyExpanded
    }

    public var id: String { title }
}

public struct ClaimItem: Equatable, Identifiable {
    public let path: ClaimItemPath
    public let pathComponents: [String]
    public let label: String
    public let value: DisplayValue
    public let rawValue: String?
    public let roles: Set<ClaimRole>

    public var id: String { path.id }

    public init(
        path: ClaimItemPath,
        pathComponents: [String] = [],
        label: String,
        value: DisplayValue,
        rawValue: String?,
        roles: Set<ClaimRole> = []
    ) {
        self.path = path
        self.pathComponents = pathComponents
        self.label = label
        self.value = value
        self.rawValue = rawValue
        self.roles = roles
    }
}

public enum DisplayValue: Equatable {
    case text(String)
    case number(String)
    case bool(Bool)
    case object([ClaimItem])
    case list([DisplayValue])
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
        ClaimItemPath(renderedID: .raw(name))
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
            case .child(let name): return "\(partial).\(name)"
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
