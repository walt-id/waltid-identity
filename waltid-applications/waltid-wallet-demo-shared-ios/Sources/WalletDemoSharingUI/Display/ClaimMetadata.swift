import Foundation

public struct CredentialClaimMetadata: Equatable {
    public let path: [String]
    public let mandatory: Bool?
    public let name: String?
}

extension ClaimItem {
    /// Metadata changes label/order only; disclosure policy and values remain SDK-owned.
    func applyingClaimMetadata(_ metadata: [CredentialClaimMetadata], format: String, expression: ClaimPathExpression? = nil) -> ClaimItem {
        let paths = metadataPaths(expression ?? ClaimPathExpression.parse(path.id), format: format)
        let match = metadata.enumerated().first { paths.contains($0.element.path) }
        return ClaimItem(path: path, pathComponents: pathComponents, label: match?.element.name ?? label,
            value: value.applyingClaimMetadata(metadata, format: format), rawValue: rawValue, roles: roles,
            labelSource: match?.element.name == nil ? labelSource : .issuerMetadata, displayOrder: match?.offset)
    }
}

private extension DisplayValue {
    func applyingClaimMetadata(_ metadata: [CredentialClaimMetadata], format: String) -> DisplayValue {
        switch self {
        case .object(let items):
            return .object(items.map { $0.applyingClaimMetadata(metadata, format: format) }.sorted { ($0.displayOrder ?? .max) < ($1.displayOrder ?? .max) })
        case .list(let values): return .list(values.map { $0.applyingClaimMetadata(metadata, format: format) })
        default: return self // Deferred images remain unresolved.
        }
    }
}

private func metadataPaths(_ expression: ClaimPathExpression, format: String) -> [[String]] {
    // String-only SDK metadata must not accidentally label a different claim after dropping an index.
    let path: [String] = expression.segments.compactMap { if case .key(let value) = $0 { return value }; return nil }
    guard path.count == expression.segments.count else { return [] }
    var paths = [path]
    if format == "mso_mdoc", path.last == "elementValue" { paths.append(Array(path.dropLast())) }
    if format == "jwt_vc_json", path.first == "vc" { paths.append(Array(path.dropFirst())) }
    return paths
}

func applyClaimMetadata(_ groups: [ClaimGroup], metadata: [CredentialClaimMetadata], format: String) -> [ClaimGroup] {
    guard !metadata.isEmpty else { return groups }
    let mapped = groups.map { group in
        if group.id == "requested" { return group }
        return ClaimGroup(id: group.id, title: group.title,
            items: group.items.map { $0.applyingClaimMetadata(metadata, format: format) },
            initiallyExpanded: group.initiallyExpanded, transactionType: group.transactionType)
    }
    let stored = mapped.filter { $0.id != "requested" }
    guard stored.contains(where: { $0.items.contains { $0.displayOrder != nil } }) else { return mapped }
    let readable = stored.flatMap { group in group.items.filter { group.id != "technical" || $0.displayOrder != nil } }
        .sorted { ($0.displayOrder ?? .max) < ($1.displayOrder ?? .max) }
    let technical = stored.filter { $0.id == "technical" }.compactMap { group -> ClaimGroup? in
        let items = group.items.filter { $0.displayOrder == nil }
        return items.isEmpty ? nil : ClaimGroup(id: group.id, title: group.title, items: items, initiallyExpanded: group.initiallyExpanded)
    }
    return mapped.filter { $0.id == "requested" }
        + (readable.isEmpty ? [] : [ClaimGroup(id: "data", title: ClaimGroupKind.other.title, items: readable)]) + technical
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
