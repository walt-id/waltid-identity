import Foundation

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
    case address
    case other
    case technical

    var title: String {
        switch self {
        case .personal: return "Personal details"
        case .address: return "Address"
        case .other: return "Credential data"
        case .technical: return "Technical claims"
        }
    }

    var order: Int {
        switch self {
        case .personal: return 0
        case .address: return 1
        case .other: return 2
        case .technical: return 3
        }
    }
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

    var id: String {
        switch self {
        case .root: return "$"
        }
    }
}
