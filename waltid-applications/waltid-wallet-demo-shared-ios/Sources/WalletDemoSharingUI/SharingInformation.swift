import Foundation
import WalletSDK

struct SharingInformationField {
    let item: ClaimItem
    let included: Bool
    let alwaysIncluded: Bool
    let optionalSelections: Set<PresentationDisclosureSelection>
    let path: [ClaimPathExpression.Segment]
}

struct SharingInformationGroup: Identifiable {
    let options: [PresentationCredentialOption]
    let details: CredentialDetails
    let fields: [SharingInformationField]
    var id: String { options[0].credentialID }
}

extension SharingReviewModel {
    /// A display union only. SDK validation and submission retain each query's selection.
    func informationToShare(selection: SharingSelection, details: [CredentialDetails]) -> [SharingInformationGroup] {
        let options = credentialOptions.filter { selection.credentials.contains($0.selection) }
        var order: [String] = []
        var grouped: [String: [PresentationCredentialOption]] = [:]
        for option in options {
            if grouped[option.credentialID] == nil { order.append(option.credentialID) }
            grouped[option.credentialID, default: []].append(option)
        }
        return order.compactMap { id in
            guard let options = grouped[id], let first = options.first,
                  let source = details.first(where: { $0.id == first.selection.id }) else { return nil }
            let fields = options.flatMap { option in
                informationFields(option: option, details: details.first(where: { $0.id == option.selection.id }) ?? source,
                    disclosures: selection.disclosures)
            }
            var paths: [[ClaimPathExpression.Segment]] = []
            var byPath: [[ClaimPathExpression.Segment]: [SharingInformationField]] = [:]
            for field in fields {
                if byPath[field.path] == nil { paths.append(field.path) }
                byPath[field.path, default: []].append(field)
            }
            let merged = paths.compactMap { path -> SharingInformationField? in
                guard let fields = byPath[path], let first = fields.first else { return nil }
                let fixed = fields.contains { $0.alwaysIncluded || ($0.included && $0.optionalSelections.isEmpty) }
                return SharingInformationField(item: first.item, included: fields.contains { $0.included },
                    alwaysIncluded: fields.contains { $0.alwaysIncluded },
                    optionalSelections: fixed ? [] : Set(fields.flatMap { $0.optionalSelections }), path: first.path)
            }.sorted { ($0.item.displayOrder ?? .max) < ($1.item.displayOrder ?? .max) }
            return SharingInformationGroup(options: options, details: source, fields: merged)
        }
    }
}

func informationFields(option: PresentationCredentialOption, details: CredentialDetails,
                       disclosures: Set<PresentationDisclosureSelection>) -> [SharingInformationField] {
    let items = details.groups.first { $0.id == "requested" }?.items ?? []
    return option.disclosures.enumerated().compactMap { index, disclosure in
        guard items.indices.contains(index) else { return nil }
        let item = items[index]
        if !disclosure.requested &&
            CredentialDisplayVocabulary.groupKind(for: item.pathComponents, format: option.format) == .technical { return nil }
        let key = PresentationDisclosureSelection(queryID: option.queryID, credentialID: option.credentialID, path: disclosure.path)
        return SharingInformationField(item: item,
            included: !disclosure.selectivelyDisclosable || disclosure.required || disclosures.contains(key),
            alwaysIncluded: !disclosure.requested,
            optionalSelections: disclosure.selectable ? [key] : [],
            path: option.format == "mso_mdoc" && disclosure.path.contains("/") && !disclosure.path.contains("://") && !disclosure.path.hasPrefix("[")
                ? disclosure.path.split(separator: "/", maxSplits: 1).map { .key(String($0)) }
                : ClaimPathExpression.parse(disclosure.path).segments)
    }
}

extension Array where Element == SharingInformationField {
    func disclosureStatus(_ item: ClaimItem) -> String {
        let matches = filter { field in
            let prefix = field.item.pathComponents
            return !prefix.isEmpty && item.pathComponents.starts(with: prefix)
        }
        if matches.contains(where: { $0.included && $0.alwaysIncluded }) { return "Always included" }
        if matches.contains(where: { $0.included }) { return "Requested" }
        return "Not shared"
    }
}
