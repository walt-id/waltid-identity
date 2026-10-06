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
        WalletNavigationContainer {
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
            .navigationTitle(viewModel.offerPreview == nil && (viewModel.issuanceReceipt != nil || !viewModel.deferredCredentials.isEmpty)
                ? "Receiving result" : "Receive credentials")
            .navigationBarTitleDisplayMode(.inline)
            .walletFlowToolbar(onBack: onBack, backEnabled: viewModel.externalFlow != nil ? viewModel.canDismissExternalFlow : !viewModel.isLoading,
                onOpenSettings: nil, external: viewModel.externalFlow != nil)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier(WalletAccessibilityID.receiveTabContent)
        }
        .id(viewModel.receiveNavigationResetKey)
        .onChange(of: viewModel.authorizationRequestURL) { authorizationURL in
            guard let authorizationURL else { return }
            openURL(authorizationURL)
            viewModel.authorizationRequestOpened()
        }
    }

    private var entryContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {

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
        .safeAreaInset(edge: .bottom, spacing: 0) { WalletTabFeedback(viewModel: viewModel, tab: .receive) }
    }

    private func reviewContent(preview: IssuanceOfferPreview) -> some View {
        WalletReviewScaffold {

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

        } actions: {
            WalletTabStatusBanner(viewModel: viewModel, tab: .receive)
            OfferReviewActions(
                requiresIssuerAuthentication: preview.grant == .authorizationCode,
                isAcceptEnabled: viewModel.acceptOfferEnabled,
                isReviewEnabled: viewModel.offerReviewEnabled,
                onAccept: viewModel.acceptOffer,
                onDecline: viewModel.declineOffer
            )
        }
    }

    private var pendingCredentials: [DeferredCredential] {
        viewModel.deferredCredentials.filter { viewModel.issuanceReceipt?.pendingIDs.contains($0.id) ?? true }
    }

    private var resultContent: some View {
        WalletReviewScaffold {
            IssuanceResultContent(receipt: viewModel.issuanceReceipt, saved: viewModel.receivedCredentials,
                pending: pendingCredentials,
                busy: viewModel.isLoading, onResume: viewModel.resumeDeferredCredential)
        } actions: {
            WalletTabStatusBanner(viewModel: viewModel, tab: .receive)
            WalletActions(primary: WalletAction("Done", enabled: !viewModel.isLoading, identifier: "issuance-done") {
                if viewModel.externalFlow != nil { viewModel.closeExternalFlow() } else { viewModel.selectedTab = .credentials }
            }, secondary: pendingCredentials.isEmpty ? nil : WalletAction("Refresh status",
                enabled: !viewModel.isLoading, identifier: "issuance-refresh", perform: viewModel.refreshIssuanceStatus))
        }
    }
}
