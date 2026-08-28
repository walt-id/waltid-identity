import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.openURL) private var openURL
    @Environment(\.walletDemoBranding) private var branding

    var body: some View {
        NavigationView {
            Group {
                if let preview = viewModel.offerPreview {
                    reviewContent(preview: preview)
                } else {
                    entryContent
                }
            }
            .navigationTitle("Receive")
            .walletSettingsToolbar(viewModel: viewModel)
            .accessibilityIdentifier(WalletAccessibilityID.receiveTabContent)
        }
        .navigationViewStyle(.stack)
        .onChange(of: viewModel.authorizationRequestURL) { authorizationURL in
            guard let authorizationURL else { return }
            openURL(authorizationURL)
            viewModel.authorizationRequestOpened()
        }
    }

    private var entryContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                WalletTabStatusBanner(viewModel: viewModel, tab: .receive)

                ScannableUrlEditor(
                    title: "Receive",
                    label: "Credential offer URL",
                    text: $viewModel.offerUrl,
                    inputIdentifier: WalletAccessibilityID.offerInput,
                    scanButtonIdentifier: WalletAccessibilityID.offerScanButton,
                    isEnabled: viewModel.receiveUrlEntryEnabled,
                    focusResetKey: viewModel.inputFocusResetKey
                )

                Button("Receive") {
                    viewModel.previewOffer()
                }
                .buttonStyle(.borderedProminent)
                .tint(branding.primary)
                .disabled(!viewModel.receiveActionEnabled)
                .accessibilityIdentifier(WalletAccessibilityID.receiveButton)

                deferredCredentials
            }
            .padding()
        }
    }

    private func reviewContent(preview: IssuanceOfferPreview) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                WalletTabStatusBanner(viewModel: viewModel, tab: .receive)

                OfferReviewView(
                    preview: preview,
                    isAcceptEnabled: viewModel.acceptOfferEnabled,
                    isReviewEnabled: viewModel.offerReviewEnabled,
                    txCode: viewModel.txCode,
                    onTxCodeChange: viewModel.updateTxCode,
                    onAccept: viewModel.acceptOffer,
                    onDecline: viewModel.declineOffer,
                    showActions: false
                )

                if let warning = viewModel.transactionDataProfilesWarning {
                    WarningBannerView(message: warning)
                }

                deferredCredentials
            }
            .padding()
        }
        .safeAreaInset(edge: .bottom) {
            OfferReviewActions(
                requiresIssuerAuthentication: preview.grant == .authorizationCode,
                isAcceptEnabled: viewModel.acceptOfferEnabled,
                isReviewEnabled: viewModel.offerReviewEnabled,
                onAccept: viewModel.acceptOffer,
                onDecline: viewModel.declineOffer
            )
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.bar)
        }
    }

    @ViewBuilder
    private var deferredCredentials: some View {
        if !viewModel.deferredCredentials.isEmpty {
            Text("Pending credentials")
                .font(.subheadline.weight(.semibold))
            ForEach(viewModel.deferredCredentials, id: \.id) { credential in
                Button("Check \(credential.credentialConfigurationID)") {
                    viewModel.resumeDeferredCredential(credential)
                }
                .buttonStyle(.bordered)
                .disabled(viewModel.isLoading)
            }
        }
    }
}
