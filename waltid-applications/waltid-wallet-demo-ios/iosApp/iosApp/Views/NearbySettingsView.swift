import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct SettingsDestinationLabel: View {
    let title: LocalizedStringKey
    let systemImage: String
    let summary: String

    init(_ title: LocalizedStringKey, systemImage: String, summary: String) {
        self.title = title
        self.systemImage = systemImage
        self.summary = summary
    }

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                Text(summary).font(.footnote).foregroundStyle(.secondary)
            }
        } icon: {
            Image(systemName: systemImage).accessibilityHidden(true)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(title))
        .accessibilityValue(summary)
    }
}

struct NearbySettingsView: View {
    @ObservedObject var viewModel: WalletViewModel
    @ObservedObject private var readerTrust: DemoReaderTrustSettingsController

    init(viewModel: WalletViewModel) {
        self.viewModel = viewModel
        self.readerTrust = viewModel.readerTrustSettings
    }

    var body: some View {
        List {
            Section("Sharing approval") {
                ProximityApprovalModeChoice(mode: $viewModel.proximityApprovalMode, compact: false)
            }
            Section {
                NavigationLink { ConnectionSettingsView(viewModel: viewModel) } label: {
                    SettingsDestinationLabel("Connection method", systemImage: "network", summary: viewModel.proximityTransportProfile.title)
                }
                .accessibilityIdentifier("wallet.settingsConnectionMethod")
                NavigationLink { ReaderTrustSettingsView(controller: readerTrust) } label: {
                    SettingsDestinationLabel("Reader authentication", systemImage: "shield", summary: readerTrust.settings.readerPolicy == .requireTrusted
                        ? String(localized: "Require trusted readers") : String(localized: "Allow anonymous or untrusted readers"))
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsReaderAuthentication)
            }
        }
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Nearby sharing")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ConnectionSettingsView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        List {
            Section { profiles([.defaultProfile, .bluetooth, .wifiAware]) }
            Section { profiles([.provisionalNfcV2Hybrid, .provisionalNfcV2Direct, .provisionalNfcV2WifiAware]) } header: {
                Text("Provisional NFC profiles")
            } footer: {
                Text("Use Automatic unless the reader requires a specific connection method. Changes update sharing before connection or approval. Otherwise, they apply to your next presentation.")
            }
        }
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Connection method")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func profiles(_ profiles: [WalletDemoProximityTransportProfile]) -> some View {
        ForEach(profiles) { profile in
            Button { viewModel.proximityTransportProfile = profile } label: {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(profile.title).foregroundStyle(.primary)
                        Text(profile.description).font(.footnote).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if viewModel.proximityTransportProfile == profile { Image(systemName: "checkmark").foregroundStyle(.tint).accessibilityHidden(true) }
                }
                .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier(profile.accessibilityIdentifier)
            .accessibilityValue(viewModel.proximityTransportProfile == profile ? "Selected" : "Not selected")
            .accessibilityAddTraits(viewModel.proximityTransportProfile == profile ? .isSelected : [])
        }
    }
}

extension WalletDemoProximityTransportProfile {
    var title: String {
        switch self {
        case .defaultProfile: String(localized: "Automatic")
        case .bluetooth: String(localized: "Bluetooth transfer")
        case .wifiAware: String(localized: "Wi-Fi Aware transfer")
        case .provisionalNfcV2Hybrid: String(localized: "NFCv2 + Bluetooth")
        case .provisionalNfcV2Direct: String(localized: "NFCv2 direct")
        case .provisionalNfcV2WifiAware: String(localized: "NFCv2 + Wi-Fi Aware")
        }
    }

    var description: String {
        switch self {
        case .defaultProfile: String(localized: "Use a connection supported by this device and the reader.")
        case .bluetooth: String(localized: "Start with NFC or QR and transfer over Bluetooth.")
        case .wifiAware: String(localized: "Start with NFC or QR. Both devices must support Wi-Fi Aware.")
        case .provisionalNfcV2Hybrid: String(localized: "Start with NFCv2 and transfer over Bluetooth.")
        case .provisionalNfcV2Direct: String(localized: "Keep the connection on NFCv2.")
        case .provisionalNfcV2WifiAware: String(localized: "Start with NFCv2 and allow Wi-Fi Aware transfer.")
        }
    }

    var accessibilityIdentifier: String {
        switch self {
        case .defaultProfile: WalletAccessibilityID.settingsProximityDefault
        case .provisionalNfcV2Hybrid: WalletAccessibilityID.settingsProximityNfcV2Hybrid
        case .provisionalNfcV2Direct: WalletAccessibilityID.settingsProximityNfcV2Direct
        default: "wallet.settingsProximity.\(rawValue)"
        }
    }
}
