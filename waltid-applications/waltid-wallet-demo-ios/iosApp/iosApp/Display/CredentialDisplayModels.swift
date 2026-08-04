import Foundation
import WalletSDK

struct CredentialDetails: Equatable, Identifiable {
    let id: String
    let title: String
    let issuer: String?
    let subject: String?
    let format: String
    let addedAt: Date?
    let groups: [ClaimGroup]
}

struct ClaimGroup: Equatable, Identifiable {
    let title: String
    let items: [ClaimItem]
    let initiallyExpanded: Bool

    init(title: String, items: [ClaimItem], initiallyExpanded: Bool = true) {
        self.title = title
        self.items = items
        self.initiallyExpanded = initiallyExpanded
    }

    var id: String { title }
}

struct ClaimItem: Equatable, Identifiable {
    let path: ClaimItemPath
    let pathComponents: [String]
    let label: String
    let value: DisplayValue
    let rawValue: String?
    let roles: Set<ClaimRole>

    var id: String { path.id }

    init(
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

enum DisplayValue: Equatable {
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

struct ClaimItemPath: Hashable {
    private let renderedID: RenderedClaimPath

    var id: String {
        renderedID.value
    }

    init(id: String) {
        self.renderedID = .raw(id)
    }

    private init(renderedID: RenderedClaimPath) {
        self.renderedID = renderedID
    }

    func indexedChild(_ index: Int) -> ClaimItemPath {
        ClaimItemPath(renderedID: renderedID.indexed(index))
    }

    func child(_ name: String) -> ClaimItemPath {
        ClaimItemPath(renderedID: renderedID.child(name))
    }

    static func root() -> ClaimItemPath {
        ClaimItemPath(renderedID: .raw(DisplayClaimPathRoot.root.id))
    }

    static func topLevel(_ name: String) -> ClaimItemPath {
        ClaimItemPath(renderedID: .raw(name))
    }

    static func transactionData(index: Int, field: DisplayTransactionDataField) -> ClaimItemPath {
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

enum ClaimGroupKind: CaseIterable {
    case personal
    case ageAttestations
    case address
    case other
    case travelDocumentData
    case technical

    var title: String {
        switch self {
        case .personal: return "Personal details"
        case .ageAttestations: return "Age attestations"
        case .address: return "Address"
        case .other: return "Credential data"
        case .travelDocumentData: return "Travel document data"
        case .technical: return "Credential metadata"
        }
    }

    var order: Int {
        switch self {
        case .personal: return 0
        case .ageAttestations: return 1
        case .address: return 2
        case .other: return 3
        case .travelDocumentData: return 4
        case .technical: return 5
        }
    }

    var initiallyExpanded: Bool {
        switch self {
        case .ageAttestations, .travelDocumentData, .technical: return false
        case .personal, .address, .other: return true
        }
    }
}

struct OfferClaimDisplay: Equatable {
    let label: String
    let inclusion: String
}

struct OfferClaimDisplayGroup: Equatable {
    let title: String
    let claims: [OfferClaimDisplay]
}

extension OfferedCredentialMetadata {
    var claimDisplayGroups: [OfferClaimDisplayGroup] {
        let entries = claims.enumerated().map { index, claim in
            let semantics = MdocClaimDisplaySemantics.describe(format: format, path: claim.path)
            let trimmedName = claim.displayName?.trimmingCharacters(in: .whitespacesAndNewlines)
            let displayName = trimmedName.flatMap { $0.isEmpty ? nil : $0 }
            return OfferClaimDisplayEntry(
                group: semantics?.group,
                sortOrder: semantics?.sortOrder ?? .max,
                sourceOrder: index,
                display: OfferClaimDisplay(
                    label: displayName
                        ?? semantics?.label
                        ?? CredentialDisplayVocabulary.humanizedLabel(claim.path.last ?? ""),
                    inclusion: claim.mandatory == true ? "Always included" : "May be included"
                )
            )
        }
        return Dictionary(grouping: entries, by: \.group)
            .sorted { ($0.key?.order ?? 0) < ($1.key?.order ?? 0) }
            .map { group, claims in
                OfferClaimDisplayGroup(
                    title: group?.rawValue ?? "Credential claims",
                    claims: claims
                        .sorted { ($0.sortOrder, $0.sourceOrder) < ($1.sortOrder, $1.sourceOrder) }
                        .map(\.display)
                )
            }
    }
}

private struct OfferClaimDisplayEntry {
    let group: MdocClaimDisplayGroup?
    let sortOrder: Int
    let sourceOrder: Int
    let display: OfferClaimDisplay
}

enum ClaimRole: Hashable {
    case givenName
    case familyName
    case temporal
    case expiryDate
    case image
    case credentialType
}

enum CredentialDisplayText {
    static let unknown = "Unknown"

    static func expires(_ date: String) -> String { "Expires \(date)" }
    static func added(_ date: String) -> String { "Added \(date)" }
}

struct DisplayClaimPath {
    let itemPath: ClaimItemPath
    let components: [String]

    static func topLevel(_ name: String) -> DisplayClaimPath {
        DisplayClaimPath(itemPath: ClaimItemPath.topLevel(name), components: [name])
    }

    static func transactionData(index: Int, field: DisplayTransactionDataField) -> DisplayClaimPath {
        DisplayClaimPath(
            itemPath: ClaimItemPath.transactionData(index: index, field: field),
            components: [DisplayClaimPathRoot.transactionData.id, field.id]
        )
    }

    func child(_ child: String) -> DisplayClaimPath {
        DisplayClaimPath(
            itemPath: itemPath.child(child),
            components: components + [child]
        )
    }

    func indexed(_ index: Int) -> DisplayClaimPath {
        DisplayClaimPath(itemPath: itemPath.indexedChild(index), components: components)
    }
}

enum DisplayClaimPathRoot {
    case root
    case transactionData

    var id: String {
        switch self {
        case .root: return "$"
        case .transactionData: return "transactionData"
        }
    }
}

enum DisplayTransactionDataField {
    case type
    case credentialQueryIDs
    case details
    case raw

    var id: String {
        switch self {
        case .type: return "type"
        case .credentialQueryIDs: return "credentialQueryIds"
        case .details: return "details"
        case .raw: return "raw"
        }
    }
}
