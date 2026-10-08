import SwiftUI
import WalletDemoSharingUI

/// Pure configuration content shared by the live setup screen and deterministic previews/tests.
struct SigningKeySetupContent: View {
    let options: [WalletIdentityScreenModel.SetupOption]
    let selected: WalletIdentityScreenModel.SetupOption
    let step: WalletIdentityScreenModel.Step
    var requestedApproval: WalletIdentityScreenModel.Selection? = nil
    let onSelect: (String) -> Void
    let onEdit: (SigningKeySetting) -> Void

    private var selections: [WalletIdentityScreenModel.Selection] {
        step.options(options, selected: selected).compactMap(step.choice).reduce(into: []) { values, next in
            if !values.contains(where: { $0.id == next.id }) { values.append(next) }
        }
    }

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                Text(stepDescription).font(.callout).foregroundStyle(.secondary)
                if step == .storage && selected.recovery.id != "new" {
                    Text("The Secure Enclave cannot restore a key. Recoverable keys use Keychain or the encrypted wallet database.").font(.callout)
                }
            }.listRowBackground(Color.clear)
        }.id("setup-top")
        if step == .recovery {
            if selections.contains(where: { !$0.id.hasPrefix("restore:") }) {
                Section("Create a new key") { selectionRows(restoring: false) }
            }
            if selections.contains(where: { $0.id.hasPrefix("restore:") }) {
                Section("Restore an existing key") { selectionRows(restoring: true) }
            }
        } else if step != .summary {
            if step == .approval, let requestedApproval, !selections.contains(where: { $0.id == requestedApproval.id }) {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(requestedApproval.title).font(.headline)
                        Text("The selected signing approval is unavailable for this configuration.").font(.callout).foregroundStyle(.secondary)
                    }.accessibilityValue("Selected, unavailable")
                }
            }
            Section { selectionRows() }
        }
        if step == .summary {
            Section {
                SigningKeySummary(
                    recovery: selected.recovery.id == "new" ? String(localized: "No key backup") : selected.recovery.title,
                    storage: selected.storage.title, approval: requestedApproval?.title ?? selected.approval.title,
                    onEdit: onEdit)
            } header: { Text("Signing key") } footer: {
                Text(selected.recovery.detail)
            }
        }
    }


    private var selectedChoiceID: String? {
        step == .approval ? (requestedApproval?.id ?? selected.approval.id) : step.choice(selected)?.id
    }

    private var stepDescription: String {
        switch step {
        case .recovery: "Create a new signing key or restore an existing one. New keys can be created with or without a backup."
        case .storage: "Choose how to store and protect your signing key. Only options compatible with your recovery choice are shown."
        case .approval: "Choose how to approve signing. This is separate from unlocking the app."
        case .summary: "Create your signing key with these settings, or tap a row to customize."
        }
    }

    private func selectionRows(restoring: Bool? = nil) -> some View {
        ForEach(Array(selections.enumerated()).filter { _, choice in
            restoring == nil || choice.id.hasPrefix("restore:") == restoring
        }, id: \.element.id) { index, choice in
            VStack(alignment: .leading, spacing: 8) {
                selectionCard(choice, selected: selectedChoiceID == choice.id)
                    .accessibilityIdentifier("wallet.keySetupChoice.\(step).\(index)")
                if let identifier = choice.identifier {
                    SettingsCopyContent(title: "Wallet DID", value: identifier, copyLabel: "Copy wallet DID", copyAnnouncement: String(localized: "Wallet DID copied"),
                        valueID: "wallet.recoveryDid.\(choice.id)", copyID: "wallet.recoveryDidCopy.\(choice.id)", disclosureLabels: ("Show full DID", "Hide full DID"))
                }
            }
            .listRowBackground(selections.count > 1 && selectedChoiceID == choice.id
                ? Color.accentColor.opacity(0.08) : Color(uiColor: .secondarySystemGroupedBackground))
        }
    }

    @ViewBuilder
    private func selectionCard(_ choice: WalletIdentityScreenModel.Selection, selected: Bool) -> some View {
        if selections.count == 1 && selected {
            VStack(alignment: .leading, spacing: 6) {
                selectionText(choice)
                Text("This is the only supported option for your current configuration.").font(.footnote).foregroundStyle(.secondary)
            }
        } else {
            Button { onSelect(choice.id) } label: {
                HStack(alignment: .center, spacing: 12) {
                    selectionText(choice)
                    Spacer(minLength: 0)
                    Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                        .foregroundStyle(.tint).accessibilityHidden(true)
                }
                .frame(minHeight: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(selected ? "Selected" : "Not selected")
            .accessibilityAddTraits(selected ? [.isSelected] : [])
        }
    }

    private func selectionText(_ choice: WalletIdentityScreenModel.Selection) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(choice.title).font(.headline).foregroundStyle(.primary)
            Text(choice.detail).font(.callout).foregroundStyle(.secondary)
        }
    }

}
