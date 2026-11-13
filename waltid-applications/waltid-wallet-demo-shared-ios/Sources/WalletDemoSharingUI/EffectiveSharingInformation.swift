import Foundation
import WalletSDK

/// Loading and failure must never authorize sharing using an earlier selection.
public enum ClaimReviewState: Equatable {
    case legacy
    case loading(EffectivePresentationReview? = nil)
    case ready(EffectivePresentationReview)
    case failed(String)

    public var canConfirm: Bool {
        switch self { case .legacy, .ready: return true; default: return false }
    }
    public var revision: String? { if case .ready(let value) = self { return value.revision }; return nil }
    public var previous: EffectivePresentationReview? {
        switch self { case .ready(let value): return value; case .loading(let value): return value; default: return nil }
    }
}

struct EffectiveClaimNode: Equatable, Identifiable {
    enum Kind: Equatable { case scalar, object, array }
    let path: [ClaimPathExpression.Segment]
    var label: String
    var kind: Kind
    var value: DisplayValue?
    var children: [EffectiveClaimNode]
    var reason: String?
    var alwaysShared: Bool
    var contextOnly: Bool
    var technical: Bool
    var initiallyExpanded: Bool
    var choice: EffectiveClaimChoice? = nil
    var id: ClaimItemPath { displayPath.itemPath }
    var displayPath: DisplayClaimPath { effectiveDisplayPath(path) }
    var usesCards: Bool { children.contains { $0.kind != .scalar } }
}

struct EffectiveClaimChoice: Equatable, Identifiable {
    let selection: PresentationDisclosureSelection
    let item: ClaimItem
    let selected: Bool
    let included: Bool
    let forcedReason: String?
    var id: String { selection.id }
    var status: String {
        forcedReason ?? (selected ? "Included" : included ? "Not selected; some information is already shared" : "Not shared")
    }
}

struct EffectiveClaimContribution: Equatable, Identifiable {
    let id: String
    let credentialID: String
    let title: String
    let wholeCredential: Bool
    let separatesAlwaysShared: Bool
    var nodes: [EffectiveClaimNode]
    var detachedChoices: [EffectiveClaimChoice]
}

/// Layout is applied only to SDK-effective values. Off choices are explicitly holder-known candidates.
public struct EffectivePresentationReview: Equatable {
    public let revision: String
    let contributions: [EffectiveClaimContribution]

    public init(prepared: PreparedPresentationReview, model: SharingReviewModel,
                details: [CredentialDetails], selection: SharingSelection) throws {
        guard !prepared.revision.isEmpty,
              prepared.credentials.count == selection.credentials.count,
              Set(prepared.credentials.map { PresentationCredentialSelection(queryID: $0.queryID, credentialID: $0.credentialID) }) == selection.credentials else {
            throw invalidReview("Effective review does not match selected credentials")
        }
        revision = prepared.revision
        let options = Dictionary(uniqueKeysWithValues: model.credentialOptions.map { ($0.selection, $0) })
        func optionFor(_ contribution: PresentationCredentialClaims) throws -> PresentationCredentialOption {
            guard let option = options[.init(queryID: contribution.queryID, credentialID: contribution.credentialID)],
                  selection.credentials.contains(option.selection), option.format == contribution.format else { throw invalidReview("Unavailable credential option") }
            return option
        }
        func path(_ raw: String, _ format: String) -> [ClaimPathExpression.Segment] {
            ClaimPathExpression.sdkDisclosure(raw, format: format).segments
        }
        func label(_ raw: String, option: PresentationCredentialOption) -> String {
            let expression = ClaimPathExpression(segments: path(raw, option.format))
            let metadata = StoredCredentialMetadataParser.claims(from: option.metadataJSON, format: option.format)
            return bestClaimMetadata(metadata, expression: expression, format: option.format)?.element.name
                ?? option.disclosures.first { $0.path == raw }?.name
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .flatMap { $0.isEmpty ? nil : CredentialDisplayVocabulary.humanizedLabel($0) }
                ?? expression.leafKey.map(CredentialDisplayVocabulary.humanizedLabel) ?? "Selected information"
        }
        var rendered: [EffectiveClaimContribution] = []
        for contribution in prepared.credentials {
            let option = try optionFor(contribution)
            guard let detail = details.first(where: { $0.id == option.selection.id }),
                  case .object(let members) = CredentialDisplayJSONParser.parse(contribution.claimsJSON) else {
                throw invalidReview("Missing effective credential data")
            }
            let metadata = StoredCredentialMetadataParser.claims(from: option.metadataJSON, format: option.format)
            var locations: [[ClaimPathExpression.Segment]: PresentationClaimLocation] = [:]
            for location in contribution.locations {
                let received = try concreteReviewPath(location.receivedPathJSON)
                guard locations.updateValue(location, forKey: received) == nil else { throw invalidReview("Duplicate effective claim identity") }
                _ = try concreteReviewPath(location.sourcePathJSON)
            }
            guard locations[[]] != nil else { throw invalidReview("Missing root identity") }
            let requested = option.disclosures.filter(\.requested)
            func makeNode(_ value: CredentialDisplayJSONValue, received: [ClaimPathExpression.Segment], fallback: String) throws -> EffectiveClaimNode {
                guard let location = locations[received] else { throw invalidReview("Effective value has no source identity") }
                let source = try concreteReviewPath(location.sourcePathJSON)
                let exact = requested.filter { reviewPathMatches(path($0.path, option.format), source) }
                let own = exact.first { location.sharedBy.contains($0.path) }
                let covered = location.sharedBy.contains { cause in
                    let parent = path(cause, option.format)
                    return parent.count < source.count && reviewPathMatches(parent, Array(source.prefix(parent.count)))
                }
                let reason: String?
                if own?.required == true { reason = "Required" }
                else if covered { reason = nil }
                else if location.alwaysCarried { reason = exact.contains(where: \.required) ? "Required" : "Always included by this credential" }
                else if let own, location.sharedBy.allSatisfy({ $0 == own.path }) { reason = "Included" }
                else if !location.sharedBy.isEmpty { reason = "Shared with " + Array(Set(location.sharedBy.map { label($0, option: option) })).sorted().joined(separator: ", ") }
                else { reason = "Also shared with selected information" }
                var node = try offeredReviewNode(value, path: source, fallback: fallback, metadata: metadata, format: option.format)
                node.contextOnly = false
                node.alwaysShared = location.alwaysCarried
                node.reason = node.kind != .scalar && exact.isEmpty ? nil : reason
                node.initiallyExpanded = !location.sharedBy.isEmpty || requested.contains {
                    let target = path($0.path, option.format)
                    return source.count <= target.count && reviewPathMatches(Array(target.prefix(source.count)), source)
                }
                switch value {
                case .object(let fields) where node.kind == .object:
                    node.children = try fields.map { try makeNode($0.value, received: received + [.key($0.key)], fallback: CredentialDisplayVocabulary.humanizedLabel($0.key)) }
                    node.children.sort { reviewMetadataOrder($0.path, metadata, option.format) < reviewMetadataOrder($1.path, metadata, option.format) }
                case .array(let values) where node.kind == .array:
                    node.children = try values.enumerated().map { try makeNode($0.element, received: received + [.index($0.offset)], fallback: "") }
                default: node.children = []
                }
                return node
            }
            var nodes = try members.compactMap { member -> EffectiveClaimNode? in
                let received: [ClaimPathExpression.Segment] = [.key(member.key)]
                guard let location = locations[received] else { throw invalidReview("Missing top-level source identity") }
                if isCredentialSummaryClaim(try concreteReviewPath(location.sourcePathJSON), format: option.format) { return nil }
                return try makeNode(member.value, received: received, fallback: CredentialDisplayVocabulary.humanizedLabel(member.key))
            }
            let fields = informationFields(option: option, details: detail, disclosures: selection.disclosures)
            var detached: [EffectiveClaimChoice] = []
            for field in fields where !field.optionalSelections.isEmpty {
                guard field.optionalSelections.count == 1, let key = field.optionalSelections.first else {
                    throw invalidReview("Ambiguous optional disclosure control")
                }
                let target = path(key.path, option.format)
                let matches = try prepared.credentials.flatMap { other -> [(PresentationCredentialClaims, PresentationClaimLocation)] in
                    guard other.credentialID == contribution.credentialID else { return [] }
                    return try other.locations.filter { reviewPathMatches(target, try concreteReviewPath($0.sourcePathJSON)) }.map { (other, $0) }
                }
                let wildcard = target.contains(.wildcard)
                var forced: String?
                if !wildcard {
                    for (other, location) in matches {
                        if location.alwaysCarried { forced = "Always included by this credential"; break }
                        let otherOption = try optionFor(other)
                        if let cause = location.sharedBy.first(where: { cause in
                            let same = other.queryID == key.queryID && cause == key.path
                            let required = otherOption.disclosures.contains { $0.path == cause && ($0.required || !$0.selectivelyDisclosable) }
                            return !same && (!selection.disclosures.contains(key) || required)
                        }) {
                            forced = other.queryID == key.queryID ? "Included with \(label(cause, option: otherOption))" : "Included by another selected requirement"
                            break
                        }
                    }
                }
                let item = ClaimItem(path: field.item.path, pathComponents: field.item.pathComponents,
                    label: label(key.path, option: option), value: field.item.value, rawValue: field.item.rawValue,
                    roles: field.item.roles, labelSource: field.item.labelSource, displayOrder: field.item.displayOrder)
                let choice = EffectiveClaimChoice(selection: key, item: item,
                    selected: selection.disclosures.contains(key), included: !matches.isEmpty, forcedReason: forced)
                if !target.isEmpty && !wildcard && (forced == nil || nodes.containsReviewPath(target)) {
                    nodes = try nodes.addingReviewChoice(target, choice: choice, metadata: metadata, format: option.format)
                } else { detached.append(choice) }
            }
            rendered.append(EffectiveClaimContribution(id: option.selection.id, credentialID: option.credentialID, title: detail.cardSummary.title,
                wholeCredential: contribution.wholeCredential, separatesAlwaysShared: ["dc+sd-jwt", "vc+sd-jwt"].contains(option.format),
                nodes: nodes, detachedChoices: detached))
        }
        // Identical values may share a view only when original/received identities also agree.
        // Keep overlapping optional controls query-specific instead of inventing a union checkbox.
        let optionalPaths = model.credentialOptions.filter { selection.credentials.contains($0.selection) }.flatMap { option in
            option.disclosures.filter(\.selectable).map { CredentialDisplayJSONValue.array([.string(option.credentialID), .string(canonicalReviewPath(path($0.path, option.format)))]).rawJSON }
        }
        let collisions = Set(optionalPaths).count != optionalPaths.count
        if collisions { contributions = rendered }
        else {
            var keys: [String] = []; var equal: [String: [EffectiveClaimContribution]] = [:]
            for (raw, display) in zip(prepared.credentials, rendered) {
                let identity = try raw.locations.map { location -> String in
                    let source = try concreteReviewPath(location.sourcePathJSON)
                    let received = try concreteReviewPath(location.receivedPathJSON)
                    return CredentialDisplayJSONValue.array([.string(canonicalReviewPath(source)), .string(canonicalReviewPath(received))]).rawJSON
                }.sorted()
                let keyParts = [raw.credentialID, raw.format, raw.claimsJSON] + identity
                let key = String(data: try JSONSerialization.data(withJSONObject: keyParts), encoding: .utf8)!
                if equal[key] == nil { keys.append(key) }
                equal[key, default: []].append(display)
            }
            contributions = try keys.map { key in
                let same = equal[key]!
                var first = same[0]
                first.nodes = try mergeEffectiveTrees(same.map(\.nodes))
                first.detachedChoices = same.flatMap(\.detachedChoices)
                return first
            }
        }
    }
}

struct EffectiveClaimGroup: Identifiable {
    let id: String
    let title: String
    let contributions: [EffectiveClaimContribution]
}
extension EffectivePresentationReview {
    var groups: [EffectiveClaimGroup] {
        var order: [String] = []; var byCredential: [String: [EffectiveClaimContribution]] = [:]
        for contribution in contributions {
            if byCredential[contribution.credentialID] == nil { order.append(contribution.credentialID) }
            byCredential[contribution.credentialID, default: []].append(contribution)
        }
        return order.map { EffectiveClaimGroup(id: $0, title: byCredential[$0]![0].title, contributions: byCredential[$0]!) }
    }
}
private func mergeEffectiveTrees(_ trees: [[EffectiveClaimNode]]) throws -> [EffectiveClaimNode] {
    guard let first = trees.first else { return [] }
    return try first.map { node in
        let same = try trees.map { siblings -> EffectiveClaimNode in
            guard let match = siblings.first(where: { $0.path == node.path }) else { throw invalidReview("Inconsistent shared claim identity") }
            return match
        }
        var merged = node
        merged.children = try mergeEffectiveTrees(same.map(\.children))
        let reasons = Array(Set(same.compactMap(\.reason))).sorted()
        merged.reason = reasons.contains("Required") ? "Required" : reasons.isEmpty ? nil : reasons.joined(separator: "; ")
        merged.alwaysShared = same.allSatisfy(\.alwaysShared)
        merged.initiallyExpanded = same.contains(where: \.initiallyExpanded)
        merged.choice = same.compactMap(\.choice).first
        return merged
    }
}

private func canonicalReviewPath(_ path: [ClaimPathExpression.Segment]) -> String {
    CredentialDisplayJSONValue.array(path.map { segment in
        switch segment { case .key(let key): return .string(key); case .index(let value): return .number(String(value)); case .wildcard: return .null }
    }).rawJSON
}
private func invalidReview(_ message: String) -> WalletError { .invalidInput(message) }
private func concreteReviewPath(_ raw: String) throws -> [ClaimPathExpression.Segment] {
    guard case .array(let values) = CredentialDisplayJSONParser.parse(raw) else { throw invalidReview("Effective path is not a JSON array") }
    return try values.map {
        switch $0 {
        case .string(let key): return .key(key)
        case .number(let literal):
            guard literal.range(of: #"^(0|[1-9][0-9]*)$"#, options: .regularExpression) != nil,
                  let index = Int(literal) else { throw invalidReview("Invalid effective array index") }
            return .index(index)
        default: throw invalidReview("Effective claim path is not concrete")
        }
    }
}
private func reviewPathMatches(_ expected: [ClaimPathExpression.Segment], _ actual: [ClaimPathExpression.Segment]) -> Bool {
    expected.count == actual.count && zip(expected, actual).allSatisfy { left, right in
        if left == right { return true }
        if case .wildcard = left, case .index = right { return true }
        return false
    }
}
private func effectiveDisplayPath(_ segments: [ClaimPathExpression.Segment]) -> DisplayClaimPath {
    var item = ClaimItemPath.root(); var keys: [String] = []
    for (index, segment) in segments.enumerated() {
        switch segment {
        case .key(let key): item = index == 0 ? .topLevel(key) : item.child(key); keys.append(key)
        case .index(let value): item = item.indexedChild(value)
        case .wildcard: preconditionFailure("Effective paths must be concrete")
        }
    }
    return DisplayClaimPath(itemPath: item, components: keys)
}
private func reviewMetadataOrder(_ path: [ClaimPathExpression.Segment], _ metadata: [CredentialClaimMetadata], _ format: String) -> Int {
    bestClaimMetadata(metadata, expression: .init(segments: path), format: format)?.offset ?? .max
}
private func offeredReviewNode(_ value: CredentialDisplayJSONValue, path: [ClaimPathExpression.Segment], fallback: String,
                               metadata: [CredentialClaimMetadata], format: String) throws -> EffectiveClaimNode {
    let displayPath = effectiveDisplayPath(path)
    let decoded = CredentialDisplayNormalizer.effectiveReviewValue(value, path: displayPath, format: format)
    let kind: EffectiveClaimNode.Kind
    switch value {
    case .object: kind = .object
    case .array:
        switch decoded { case .list: kind = .array; default: kind = .scalar }
    default: kind = .scalar
    }
    let children: [EffectiveClaimNode]
    switch value {
    case .object(let fields) where kind == .object:
        children = try fields.map { try offeredReviewNode($0.value, path: path + [.key($0.key)],
            fallback: CredentialDisplayVocabulary.humanizedLabel($0.key), metadata: metadata, format: format) }
            .sorted { reviewMetadataOrder($0.path, metadata, format) < reviewMetadataOrder($1.path, metadata, format) }
    case .array(let values) where kind == .array:
        children = try values.enumerated().map { try offeredReviewNode($0.element, path: path + [.index($0.offset)], fallback: "", metadata: metadata, format: format) }
    default: children = []
    }
    return EffectiveClaimNode(path: path,
        label: bestClaimMetadata(metadata, expression: .init(segments: path), format: format)?.element.name ?? fallback,
        kind: kind, value: kind == .scalar ? decoded : nil, children: children, reason: nil, alwaysShared: false,
        contextOnly: true, technical: CredentialDisplayVocabulary.groupKind(for: displayPath.components, format: format) == .technical, initiallyExpanded: true)
}

extension Array where Element == EffectiveClaimNode {
    func containsReviewPath(_ path: [ClaimPathExpression.Segment]) -> Bool { contains { $0.path == path || $0.children.containsReviewPath(path) } }
    func sharingSection(always: Bool) -> [EffectiveClaimNode] {
        compactMap { node in
            if !always && node.contextOnly && node.choice != nil { return node }
            var copy = node
            let children = node.children.sharingSection(always: always)
            let belongs = !node.contextOnly && node.alwaysShared == always
            if always || (node.children.isEmpty && node.alwaysShared && !node.contextOnly) { copy.choice = nil }
            if node.children.isEmpty {
                guard belongs || copy.choice != nil else { return nil }
            } else {
                guard !children.isEmpty || copy.choice != nil else { return nil }
                copy.children = children
                copy.contextOnly = node.contextOnly || !belongs || children.count != node.children.count
            }
            if always || !belongs { copy.reason = nil }
            return copy
        }
    }
    fileprivate func addingReviewChoice(_ target: [ClaimPathExpression.Segment], choice: EffectiveClaimChoice,
                                       metadata: [CredentialClaimMetadata], format: String, depth: Int = 0) throws -> [EffectiveClaimNode] {
        let prefix = [ClaimPathExpression.Segment](target.prefix(depth + 1))
        let existing = first { $0.path == prefix }
        var node: EffectiveClaimNode
        if depth == target.count - 1 {
            if let existing { node = existing }
            else if let raw = choice.item.rawValue, let value = CredentialDisplayJSONParser.parse(raw) {
                node = try offeredReviewNode(value, path: prefix, fallback: choice.item.label, metadata: metadata, format: format)
            } else {
                node = EffectiveClaimNode(path: prefix, label: choice.item.label, kind: .scalar, value: choice.item.value,
                    children: [], reason: nil, alwaysShared: false, contextOnly: true, technical: false, initiallyExpanded: true)
            }
            node.choice = choice
        } else {
            let kind: EffectiveClaimNode.Kind
            if case .index = target[depth + 1] { kind = .array } else { kind = .object }
            if let existing { node = existing }
            else {
                let fallback: String
                if case .key(let key) = target[depth] { fallback = CredentialDisplayVocabulary.humanizedLabel(key) } else { fallback = "" }
                node = EffectiveClaimNode(path: prefix,
                    label: bestClaimMetadata(metadata, expression: .init(segments: prefix), format: format)?.element.name ?? fallback,
                    kind: kind, value: nil, children: [], reason: nil, alwaysShared: false, contextOnly: true,
                    technical: CredentialDisplayVocabulary.groupKind(for: effectiveDisplayPath(prefix).components, format: format) == .technical, initiallyExpanded: true)
            }
            guard node.kind == kind else { throw invalidReview("Optional claim context conflicts with effective data") }
            node.children = try node.children.addingReviewChoice(target, choice: choice, metadata: metadata, format: format, depth: depth + 1)
            node.children.sort {
                if kind == .array, case .index(let left) = $0.path.last, case .index(let right) = $1.path.last { return left < right }
                return reviewMetadataOrder($0.path, metadata, format) < reviewMetadataOrder($1.path, metadata, format)
            }
        }
        return existing == nil ? self + [node] : map { $0.path == prefix ? node : $0 }
    }
}
