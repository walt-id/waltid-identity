import SwiftUI
import WalletSDK

struct SharingCredentialRow: View {
    let option: PresentationCredentialOption
    let details: CredentialDetails
    let selection: SharingSelection
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let hasAlternatives: Bool

    var body: some View {
        let extras = informationFields(option: option, details: details, disclosures: []).filter { $0.alwaysIncluded }
            .map { $0.item.label }.reduce(into: [String]()) { labels, label in
                if !labels.contains(label) { labels.append(label) }
            }
        ReviewCredentialChoice(selected: selection.credentials.contains(option.selection), multiple: option.multiple || !hasAlternatives,
            enabled: !isLoading, readOnly: isReadOnly,
            hint: extras.isEmpty ? nil : "Also shares " + extras.prefix(2).joined(separator: ", ")
                + (extras.count > 2 ? " and \(extras.count - 2) more" : ""),
            onSelect: { onToggleCredential(option.selection) }) {
                CredentialSummaryRow(summary: details.cardSummary)
                    .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.selection.id))
            }
            .accessibilityIdentifier(WalletAccessibilityID.presentationCredentialToggle(option.selection.id))
    }
}
