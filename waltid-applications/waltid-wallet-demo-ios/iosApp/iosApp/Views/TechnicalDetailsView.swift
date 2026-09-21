import SwiftUI
import WalletDemoSharingUI
import UIKit

struct TechnicalDetailsView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        List {
            Section { SettingsCopyContent(title: "Wallet DID", value: viewModel.did, copyLabel: "Copy wallet DID", copyAnnouncement: String(localized: "Wallet DID copied"),
                                          valueID: WalletAccessibilityID.settingsDid, copyID: WalletAccessibilityID.settingsDidCopy) }
            Section { SettingsCopyContent(title: "Key ID", value: viewModel.keyID, copyLabel: "Copy key ID", copyAnnouncement: String(localized: "Key ID copied"),
                                          valueID: WalletAccessibilityID.settingsKeyId, copyID: WalletAccessibilityID.settingsKeyIdCopy) }
            Section { SettingsCopyContent(title: "Public key (JWK)", value: viewModel.publicJWK, copyLabel: "Copy public key as JWK", copyAnnouncement: String(localized: "Public key copied"),
                                          valueID: WalletAccessibilityID.settingsPublicJwk, copyID: WalletAccessibilityID.settingsPublicJwkCopy, disclosureLabels: ("Show public key", "Hide public key"), json: true) }
        }
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Technical details")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct SettingsCopyContent: View {
    let title: LocalizedStringKey
    let value: String
    let copyLabel: LocalizedStringKey
    let copyAnnouncement: String
    let valueID: String
    let copyID: String
    var disclosureLabels: (show: LocalizedStringKey, hide: LocalizedStringKey)?
    var json = false
    @State private var expanded = false
    @State private var copied = false

    private var displayValue: String {
        guard json, let data = value.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data),
              let formatted = try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys]),
              let result = String(data: formatted, encoding: .utf8) else { return value }
        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title).font(.headline)
                Spacer()
                if let disclosureLabels {
                    Button { expanded.toggle() } label: {
                        Image(systemName: expanded ? "chevron.up" : "chevron.down").frame(minWidth: 44, minHeight: 44)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(Text(expanded ? disclosureLabels.hide : disclosureLabels.show))
                }
                Button {
                    UIPasteboard.general.string = value
                    copied = true
                    UIAccessibility.post(notification: .announcement, argument: copyAnnouncement)
                } label: {
                    Image(systemName: "doc.on.doc").frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.borderless)
                .disabled(value.isEmpty)
                .accessibilityLabel(Text(copyLabel))
                .help(Text(copyLabel))
                .accessibilityIdentifier(copyID)
            }
            if disclosureLabels == nil || expanded {
                Text(value.isEmpty ? String(localized: "Not available") : displayValue)
                    .font(.footnote.monospaced())
                    .textSelection(.enabled)
                    .accessibilityIdentifier(valueID)
            }
            if copied { Text("Copied").font(.caption).foregroundStyle(.secondary) }
        }
        .task(id: copied) {
            if copied {
                do { try await Task.sleep(nanoseconds: 2_000_000_000); copied = false }
                catch { }
            }
        }
    }
}
