import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    let onOpenSettings: () -> Void
    var onBack: (() -> Void)? = nil
    @Environment(\.openURL) private var openURL
    @Environment(\.walletDemoBranding) private var branding

    var body: some View {
        NavigationView {
            Group {
                if case .unavailableCallback = viewModel.externalFlow {
                    Text("The original receiving session is no longer available. Check your wallet before starting again.")
                        .padding(20).accessibilityIdentifier("wallet.external.unavailable")
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                } else if let preview = viewModel.offerPreview {
                    reviewContent(preview: preview)
                } else if viewModel.issuanceReceipt != nil || !viewModel.deferredCredentials.isEmpty {
                    resultContent
                } else if viewModel.externalFlow != nil {
                    WalletExternalFlowStatus(viewModel: viewModel, onRetry: viewModel.previewOffer)
                } else {
                    entryContent
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Receive credentials")
            .navigationBarTitleDisplayMode(.inline)
            .walletFlowToolbar(onBack: onBack, backEnabled: viewModel.externalFlow != nil ? viewModel.canDismissExternalFlow : !viewModel.isLoading,
                onOpenSettings: viewModel.externalFlow == nil ? onOpenSettings : nil, external: viewModel.externalFlow != nil)
            .accessibilityElement(children: .contain)
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
                    title: "",
                    label: "Credential offer URL",
                    text: $viewModel.offerUrl,
                    inputIdentifier: WalletAccessibilityID.offerInput,
                    scanButtonIdentifier: WalletAccessibilityID.offerScanButton,
                    isEnabled: viewModel.receiveUrlEntryEnabled,
                    focusResetKey: viewModel.inputFocusResetKey
                )

                WalletActions(primary: WalletAction("Receive", enabled: viewModel.receiveActionEnabled,
                    identifier: WalletAccessibilityID.receiveButton, perform: viewModel.previewOffer))

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
                    copies: viewModel.issuanceCopyCounts,
                    onCopiesChange: viewModel.updateIssuanceCopies,
                    txCode: viewModel.txCode,
                    onTxCodeChange: viewModel.updateTxCode,
                    onAccept: viewModel.acceptOffer,
                    onDecline: viewModel.declineOffer,
                    showActions: false
                )

                if let warning = viewModel.transactionDataProfilesWarning {
                    WarningBannerView(message: warning)
                }

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

    private var pendingCredentials: [DeferredCredential] {
        viewModel.deferredCredentials.filter { viewModel.issuanceReceipt?.pendingIDs.contains($0.id) ?? true }
    }

    private var resultContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                WalletTabStatusBanner(viewModel: viewModel, tab: .receive)
                IssuanceResultContent(receipt: viewModel.issuanceReceipt, saved: viewModel.receivedCredentials,
                    pending: pendingCredentials,
                    busy: viewModel.isLoading, onResume: viewModel.resumeDeferredCredential)
            }.padding()
        }
        .safeAreaInset(edge: .bottom) {
            WalletActions(primary: WalletAction("Done", enabled: !viewModel.isLoading, identifier: "issuance-done") {
                if viewModel.externalFlow != nil { viewModel.closeExternalFlow() } else { viewModel.selectedTab = .credentials }
            }, secondary: pendingCredentials.isEmpty ? nil : WalletAction("Refresh status",
                enabled: !viewModel.isLoading, identifier: "issuance-refresh", perform: viewModel.refreshIssuanceStatus))
                .padding().background(.bar)
        }
    }
}
