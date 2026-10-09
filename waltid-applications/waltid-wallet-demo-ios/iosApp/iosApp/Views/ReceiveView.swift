import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    var onBack: (() -> Void)? = nil
    @Environment(\.openURL) private var openURL

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
                } else {
                    WalletRequestStatus(viewModel: viewModel, tab: .receive,
                        onRetry: viewModel.previewOffer, onClose: finishReceiving)
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(viewModel.offerPreview == nil && (viewModel.issuanceReceipt != nil || !viewModel.deferredCredentials.isEmpty)
                ? "Receiving result" : "Receive credentials")
            .navigationBarTitleDisplayMode(.inline)
            .walletFlowToolbar(onBack: onBack, backEnabled: viewModel.externalFlow != nil ? viewModel.canDismissExternalFlow : !viewModel.isLoading,
                external: viewModel.externalFlow != nil)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier(WalletAccessibilityID.receiveTabContent)
        }
        .walletDetailDismissal(perform: onBack,
            enabled: viewModel.externalFlow != nil ? viewModel.canDismissExternalFlow : !viewModel.isLoading,
            identifier: viewModel.externalFlow != nil ? "wallet.external.close" : "wallet.flowBack")
        .id(viewModel.receiveNavigationResetKey)
        .onChange(of: viewModel.authorizationRequestURL) { authorizationURL in
            guard let authorizationURL else { return }
            openURL(authorizationURL)
            viewModel.authorizationRequestOpened()
        }
    }

    private func reviewContent(preview: IssuanceOfferPreview) -> some View {
        WalletReviewScaffold {
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

        } actions: {
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
            WalletTabStatusBanner(viewModel: viewModel, tab: .receive)
            IssuanceResultContent(receipt: viewModel.issuanceReceipt, saved: viewModel.receivedCredentials,
                pending: pendingCredentials,
                busy: viewModel.isLoading, onResume: viewModel.resumeDeferredCredential)
        } actions: {
            WalletActions(primary: WalletAction("Done", enabled: !viewModel.isLoading, identifier: "issuance-done") {
                finishReceiving()
            }, secondary: pendingCredentials.isEmpty ? nil : WalletAction("Refresh status",
                enabled: !viewModel.isLoading, identifier: "issuance-refresh", perform: viewModel.refreshIssuanceStatus))
        }
        .walletSuccessDismissal(key: viewModel.receiveNavigationResetKey,
            enabled: viewModel.receiveCompleted && !viewModel.isLoading && !viewModel.statusIsError(for: .receive)
                && viewModel.issuanceReceipt?.problem == nil && pendingCredentials.isEmpty
                && !viewModel.receivedCredentials.isEmpty, onDone: finishReceiving)
    }

    private func finishReceiving() {
        if viewModel.externalFlow != nil { viewModel.closeExternalFlow() }
        else { viewModel.selectedTab = .credentials }
    }
}
