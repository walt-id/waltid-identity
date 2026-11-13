import Foundation

public struct CredentialClaimMetadata: Equatable {
    public let path: [CredentialClaimPathSegment]
    public let mandatory: Bool?
    public let name: String?
}

/// Keep metadata integers as literals: mdoc map keys can exceed the platform Int range.
public enum CredentialClaimPathSegment: Hashable {
    case key(String)
    case integer(String)
    case wildcard

    init?(_ value: CredentialDisplayJSONValue) {
        switch value {
        case .string(let key): self = .key(key)
        case .number(let literal) where literal.range(of: #"^-?(0|[1-9][0-9]*)$"#, options: .regularExpression) != nil:
            self = .integer(literal)
        case .null: self = .wildcard
        default: return nil
        }
    }

    fileprivate func matches(_ actual: ClaimPathExpression.Segment) -> Bool {
        switch (self, actual) {
        case (.key(let expected), .key(let value)): return expected == value
        case (.integer(let expected), .index(let value)): return Int(expected) == value
        case (.wildcard, .wildcard): return true
        case (.wildcard, .index(let value)): return value >= 0
        default: return false
        }
    }
}

extension CredentialClaimMetadata {
    func displayLabel(format: String) -> String {
        if let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return name }
        let keys = path.compactMap { segment -> String? in
            if case .key(let key) = segment { return key }; return nil
        }
        if keys.count == path.count,
           let semantics = MdocClaimDisplaySemantics.describe(format: format, path: keys) {
            return semantics.label
        }
        let key = keys.last ?? ""
        return key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Claim" : CredentialDisplayVocabulary.humanizedLabel(key)

    }
}

extension ClaimItem {
    /// Metadata changes label/order only; disclosure policy and values remain SDK-owned.
    func applyingClaimMetadata(_ metadata: [CredentialClaimMetadata], format: String, expression: ClaimPathExpression? = nil) -> ClaimItem {
        let pathExpression = expression ?? ClaimPathExpression.parse(path.id)
        let match = bestClaimMetadata(metadata, expression: pathExpression, format: format)
        return ClaimItem(path: path, pathComponents: pathComponents, label: match?.element.name ?? label,
            value: value.applyingClaimMetadata(metadata, format: format, expression: pathExpression), rawValue: rawValue, roles: roles,
            labelSource: match?.element.name == nil ? labelSource : .issuerMetadata, displayOrder: match?.offset)
    }
}

func bestClaimMetadata(_ metadata: [CredentialClaimMetadata], expression: ClaimPathExpression, format: String)
    -> (offset: Int, element: CredentialClaimMetadata)? {
    guard !expression.segments.isEmpty else { return nil }
    let paths = metadataPaths(expression, format: format)
    let match = metadata.enumerated().first { entry in
        paths.contains { candidate in
            entry.element.path.count == candidate.count && zip(entry.element.path, candidate).allSatisfy { $0.matches($1) }
        }
    }
    return match
}

private extension DisplayValue {
    func applyingClaimMetadata(_ metadata: [CredentialClaimMetadata], format: String, expression: ClaimPathExpression) -> DisplayValue {
        guard !expression.segments.isEmpty else { return self }
        switch self {
        case .object(let items):
            return .object(items.map { $0.applyingClaimMetadata(metadata, format: format) }.sorted { ($0.displayOrder ?? .max) < ($1.displayOrder ?? .max) })
        case .list(let values, _):
            let paths = values.indices.map { ClaimPathExpression(segments: expression.segments + [.index($0)]) }
            var labels: [Int: String] = [:]
            let mapped = values.enumerated().map { index, value in
                if let name = bestClaimMetadata(metadata, expression: paths[index], format: format)?.element.name { labels[index] = name }
                return value.applyingClaimMetadata(metadata, format: format, expression: paths[index])
            }
            return .list(mapped, labels: labels)
        default: return self // Deferred images remain unresolved.
        }
    }
}

private func metadataPaths(_ expression: ClaimPathExpression, format: String) -> [[ClaimPathExpression.Segment]] {
    let path = expression.segments
    var paths = [path]
    if format == "mso_mdoc", path.last == .key("elementValue") { paths.append(Array(path.dropLast())) }
    if format == "jwt_vc_json", path.first == .key("vc") { paths.append(Array(path.dropFirst())) }
    return paths
}

func applyClaimMetadata(_ groups: [ClaimGroup], metadata: [CredentialClaimMetadata], format: String) -> [ClaimGroup] {
    guard !metadata.isEmpty else { return groups }
    // Order only siblings; preserve semantic sections and each nested parent.
    return groups.map { group in
        if group.id == "requested" { return group }
        let items = group.items.map { $0.applyingClaimMetadata(metadata, format: format) }
        return ClaimGroup(id: group.id, title: group.title,
            items: items.sorted { ($0.displayOrder ?? .max) < ($1.displayOrder ?? .max) },
            initiallyExpanded: group.initiallyExpanded, transactionType: group.transactionType)
    }
}

func disclosurePathComponents(_ raw: String, format: String) -> [String] {
    disclosurePathExpression(raw, format: format).segments.compactMap {
        if case .key(let value) = $0 { return value }; return nil
    }
}

func disclosurePathExpression(_ raw: String, format: String) -> ClaimPathExpression {
    if format == "mso_mdoc", !raw.hasPrefix("["), raw.contains("/"), !raw.contains("://") {
        return ClaimPathExpression(segments: raw.split(separator: "/", maxSplits: 1).map { .key(String($0)) })
    }
    return ClaimPathExpression.parse(raw)
}
