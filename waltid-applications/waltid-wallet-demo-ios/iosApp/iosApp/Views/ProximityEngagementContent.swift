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
    @State private var sectionHeights: [ProximityEngagementSection: CGFloat] = [:]

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                if viewModel.displayedEngagement == .qr {
                    qrContent(payload: viewModel.qrPayload, viewport: geometry.size)
                } else {
                    VStack(alignment: .leading, spacing: 12) {
                        header
                        if viewModel.displayedEngagement == nil { choices }
                        footer
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .onPreferenceChange(ProximityEngagementHeightKey.self) { sectionHeights = $0 }
        .sheet(isPresented: $showApprovedData) {
            NavigationView {
                ScrollView {
                    if let sharing = viewModel.preparedSharing {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Only the approved reader and data can be used. Cancel to withdraw this approval.")
                            ProximityDisclosureSummary(review: sharing.review, submission: sharing.submission, credentialDetailsByID: credentialDetailsByID, initiallyExpanded: true)
                        }.padding()
                    }
                }
                .navigationTitle("Approved data").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showApprovedData = false } } }
            }.navigationViewStyle(.stack)
        }
    }

    /// Measure actual wrapped text, leaving a 200–360 point square for the QR.
    /// The content grows into the scroll view only when readable controls need more room.
    private func qrContent(payload: String?, viewport: CGSize) -> some View {
        let gap: CGFloat = 12
        let landscape = viewport.width >= 600 && viewport.width > viewport.height
        let top = sectionHeights[.header] ?? 0
        let bottom = sectionHeights[.footer] ?? 0
        let maximum = min(viewport.width, 360)
        let minimum = min(maximum, 200)
        let side = landscape
            ? min(maximum, (viewport.width - gap) / 2, max(1, viewport.height))
            : min(maximum, max(minimum, viewport.height - top - bottom - gap * 2))
        let height = max(viewport.height, landscape ? max(side, top + bottom + gap) : top + side + bottom + gap * 2)
        let code = Group {
            if let payload {
                ProximityQRCode(payload: payload)
                    .equatable()
                    .accessibilityIdentifier(WalletAccessibilityID.proximityQRCode)
            } else {
                ProgressView()
            }
        }.frame(width: side, height: side)
        return Group {
            if landscape {
                HStack(alignment: .top, spacing: gap) {
                    code
                    VStack(alignment: .leading, spacing: gap) {
                        measuredHeader
                        Spacer(minLength: 0)
                        measuredFooter
                    }
                }
            } else {
                VStack(spacing: 0) {
                    measuredHeader
                    Spacer(minLength: gap)
                    code
                    Spacer(minLength: gap)
                    measuredFooter
                }
            }
        }
        .frame(width: viewport.width, height: height)
    }

    private var measuredHeader: some View {
        header.background(GeometryReader { geometry in
            Color.clear.preference(key: ProximityEngagementHeightKey.self, value: [.header: geometry.size.height])
        })
    }

    private var measuredFooter: some View {
        footer.fixedSize(horizontal: false, vertical: true).background(GeometryReader { geometry in
            Color.clear.preference(key: ProximityEngagementHeightKey.self, value: [.footer: geometry.size.height])
        })
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
        VStack(alignment: .leading, spacing: 4) {
            if viewModel.preparedSharing == nil {
                ProximityApprovalModeChoice(mode: $approvalMode)
                    .disabled(viewModel.refreshingEngagement)
            } else {
                Button("Approved data") { showApprovedData = true }.frame(maxWidth: .infinity, minHeight: 44)
            }
            if let method = viewModel.displayedEngagement, viewModel.engagementChoices.count > 1 {
                Button(method == .qr ? String(localized: "Hold near the reader instead") : String(localized: "Show QR code instead")) {
                    viewModel.showEngagement(method == .qr ? .nfc : .qr)
                }.frame(maxWidth: .infinity, minHeight: 44)
                    .disabled(viewModel.refreshingEngagement)
            }
            if let onConnectionOptions {
                WalletSection { WalletNavigationRow("Connection options", symbol: "network", action: onConnectionOptions)
                    .disabled(!viewModel.canChangeConnectionOptions)
                    .accessibilityIdentifier("proximity-connection-options") }
            }
            if let route = viewModel.connectedRoute { ProximityConnectionDetails(route: route) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var choices: some View {
        WalletSection {
            ForEach(Array(viewModel.engagementChoices.enumerated()), id: \.offset) { index, method in
                if index > 0 { Divider() }
                WalletNavigationRow(method == .qr ? String(localized: "Show QR code") : String(localized: "Hold near the reader"),
                    subtitle: method == .qr ? String(localized: "Let the reader scan your screen.") : String(localized: "Bring your phone close to connect."),
                    symbol: method == .qr ? "qrcode" : "wave.3.right") { viewModel.showEngagement(method) }
                    .disabled(viewModel.refreshingEngagement)
                    .accessibilityIdentifier(method == .qr ? "proximity-show-Qr" : "proximity-show-Nfc")
            }
        }
    }

}

private enum ProximityEngagementSection { case header, footer }

private struct ProximityEngagementHeightKey: PreferenceKey {
    static let defaultValue: [ProximityEngagementSection: CGFloat] = [:]
    static func reduce(value: inout [ProximityEngagementSection: CGFloat], nextValue: () -> [ProximityEngagementSection: CGFloat]) {
        value.merge(nextValue(), uniquingKeysWith: { _, next in next })
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
