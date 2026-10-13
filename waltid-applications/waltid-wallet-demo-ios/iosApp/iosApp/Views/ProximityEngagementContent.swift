import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityEngagementContent: View {
    @ObservedObject var viewModel: ProximityPresentationViewModel
    @Binding var approvalMode: WalletDemoProximityApprovalMode
    let credentialDetailsByID: [String: CredentialDetails]
    var onConnectionOptions: (() -> Void)? = nil
    @State private var showApprovedData = false

    var body: some View {
        GeometryReader { geometry in
            WalletSheetScrollView {
                if viewModel.displayedEngagement == .qr {
                    qrContent(payload: viewModel.qrPayload, viewport: geometry.size)
                } else if viewModel.displayedEngagement == nil && viewModel.preparedSharing == nil {
                    ProximityEngagementMenu(methods: viewModel.engagementChoices, approvalMode: $approvalMode,
                        enabled: !viewModel.refreshingEngagement, canChangeConnectionOptions: viewModel.canChangeConnectionOptions,
                        onShowEngagement: viewModel.showEngagement,
                        onConnectionOptions: onConnectionOptions)
                } else {
                    VStack(alignment: .leading, spacing: 12) {
                        header
                        if viewModel.displayedEngagement == .nfc {
                            NfcEngagementIllustration(active: !viewModel.refreshingEngagement)
                        }
                        if viewModel.displayedEngagement == nil { choices }
                        footer.padding(.top, 12)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .onChange(of: viewModel.preparedSharing.map { ObjectIdentifier($0) }) { _ in showApprovedData = false }
        .sheet(isPresented: $showApprovedData) {
            WalletDetailSheet("Approved data", onDismiss: { showApprovedData = false }, closeIdentifier: "proximity-approved-data-close") {
                if let sharing = viewModel.preparedSharing {
                    Text("Only the approved reader and data can be used. Cancel to withdraw this approval.")
                    ProximityDisclosureSummary(review: sharing.review, submission: sharing.submission, credentialDetailsByID: credentialDetailsByID, initiallyExpanded: true)
                }
            }
        }
    }

    /// The QR has a readable natural size; long text scrolls within the sheet's available height.
    private func qrContent(payload: String?, viewport: CGSize) -> some View {
        let gap: CGFloat = 12
        let landscape = viewport.width >= 600 && viewport.width > viewport.height
        let maximum = min(viewport.width, 360)
        let side = landscape
            ? min(maximum, (viewport.width - gap) / 2)
            : maximum
        let code = Group {
            if let payload {
                ProximityQRCode(payload: payload)
                    .equatable()
                    .accessibilityIdentifier(WalletAccessibilityID.proximityQRCode)
            } else {
                VStack(spacing: 16) {
                    ProgressView()
                    Text("Preparing QR code…").font(.subheadline).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
            }
        }.frame(width: side, height: side)
        return Group {
            if landscape {
                HStack(alignment: .top, spacing: gap) {
                    code
                    VStack(alignment: .leading, spacing: gap) {
                        header
                        Spacer(minLength: 0)
                        footer
                    }.frame(minHeight: side)
                }
            } else {
                VStack(spacing: gap) {
                    header
                    code
                    footer
                }
            }
        }
        .frame(width: viewport.width)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            if viewModel.preparedSharing != nil || viewModel.displayedEngagement != nil { Text(viewModel.preparedSharing != nil ? String(localized: "Ready for one share")
                : viewModel.displayedEngagement == .qr ? String(localized: "Show QR code")
                : viewModel.displayedEngagement == .nfc ? String(localized: "Hold near the reader")
                : String(localized: "Share in person"))
                .font(.title3.bold()).accessibilityAddTraits(.isHeader) }
            if let sharing = viewModel.preparedSharing {
                Text(Array(Set(sharing.review.readerAuthentication.compactMap(\.displayName))).sorted().joined(separator: ", "))
                    .font(.subheadline.weight(.semibold))
                ProximityPreparedSharingCountdown(sharing: sharing)
            }
            Text(viewModel.displayedEngagement == nil ? String(localized: "Choose how to connect to the reader.")
                : viewModel.preparedSharing != nil ? String(localized: "Start a new request on the reader, then reconnect.")
                : viewModel.displayedEngagement == .qr ? String(localized: "Let the reader scan this code. Keep both devices nearby.")
                : String(localized: "Keep your phone near the reader while it connects."))
                .font(.subheadline).foregroundStyle(.secondary)
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let method = viewModel.displayedEngagement, viewModel.engagementChoices.count > 1 {
                Button(method == .qr ? String(localized: "Hold near the reader instead") : String(localized: "Show QR code instead")) {
                    viewModel.showEngagement(method == .qr ? .nfc : .qr)
                }.frame(maxWidth: .infinity, minHeight: 44)
                    .disabled(viewModel.refreshingEngagement)
            }
            ProximitySharingOptions(approvalMode: $approvalMode, prepared: viewModel.preparedSharing != nil,
                enabled: !viewModel.refreshingEngagement, canChangeConnectionOptions: viewModel.canChangeConnectionOptions,
                onApprovedData: { showApprovedData = true }, onConnectionOptions: onConnectionOptions)
            if let route = viewModel.connectedRoute { ProximityConnectionDetails(route: route) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var choices: some View {
        ProximityConnectionChoices(methods: viewModel.engagementChoices, enabled: !viewModel.refreshingEngagement,
            onShowEngagement: viewModel.showEngagement)
    }

}

/// Capability-backed connection choices before the timed session starts.
struct ProximityEngagementMenu: View {
    let methods: [ProximityEngagementMethod]
    @Binding var approvalMode: WalletDemoProximityApprovalMode
    var enabled = true
    var canChangeConnectionOptions = true
    let onShowEngagement: (ProximityEngagementMethod) -> Void
    var onConnectionOptions: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Choose how to connect to the reader.")
                .font(.subheadline).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            ProximityConnectionChoices(methods: methods, enabled: enabled, onShowEngagement: onShowEngagement)
            ProximitySharingOptions(approvalMode: $approvalMode, enabled: enabled,
                canChangeConnectionOptions: canChangeConnectionOptions, onConnectionOptions: onConnectionOptions)
                .padding(.top, 12)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ProximityConnectionChoices: View {
    let methods: [ProximityEngagementMethod]
    let enabled: Bool
    let onShowEngagement: (ProximityEngagementMethod) -> Void

    var body: some View {
        WalletSection {
            VStack(spacing: 0) {
                ForEach(Array(methods.enumerated()), id: \.offset) { index, method in
                    if index > 0 { Divider().padding(.horizontal, 16) }
                    WalletNavigationRow(method == .qr ? String(localized: "Show QR code") : String(localized: "Hold near the reader"),
                        subtitle: method == .qr ? String(localized: "Let the reader scan your screen.") : String(localized: "Bring your phone close to connect."),
                        symbol: method == .qr ? "qrcode" : "wave.3.right") { onShowEngagement(method) }
                        .disabled(!enabled)
                        .accessibilityIdentifier(method == .qr ? "proximity-show-Qr" : "proximity-show-Nfc")
                }
            }
        }
    }

}

private struct ProximitySharingOptions: View {
    @Binding var approvalMode: WalletDemoProximityApprovalMode
    var prepared = false
    var enabled = true
    var canChangeConnectionOptions = true
    var onApprovedData: () -> Void = {}
    var onConnectionOptions: (() -> Void)? = nil

    var body: some View {
        WalletSection {
            VStack(spacing: 0) {
                if prepared {
                    Button("Approved data", action: onApprovedData).frame(maxWidth: .infinity, minHeight: 44)
                } else {
                    ProximityApprovalModeChoice(mode: $approvalMode)
                        .padding(.horizontal, 16).padding(.vertical, 8).disabled(!enabled)
                }
                if let onConnectionOptions {
                    Divider().padding(.horizontal, 16)
                    WalletNavigationRow("Connection options", symbol: "network", action: onConnectionOptions)
                        .disabled(!canChangeConnectionOptions)
                        .accessibilityIdentifier("proximity-connection-options")
                }
            }
        }
    }
}

private struct ProximityQRCode: View, Equatable {
    let payload: String

    var body: some View {
        Group {
            if let image = WalletQRCodeRenderer.proximityImage(payload: payload) {
                WalletQRCodeView(image: image)
            } else {
                Text("The device engagement QR code could not be rendered.")
                    .foregroundStyle(.red)
            }
        }
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityLabel("Device engagement QR code")
    }
}
