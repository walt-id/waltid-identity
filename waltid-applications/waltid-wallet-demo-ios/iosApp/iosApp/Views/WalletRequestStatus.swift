import SwiftUI
import WalletDemoSharingUI

/// Preparation and recovery belong to the current request, including requests opened by the scanner.
struct WalletRequestStatus: View {
    @ObservedObject var viewModel: WalletViewModel
    let tab: WalletTab
    let onRetry: () -> Void
    let onClose: () -> Void

    private var canRetry: Bool {
        switch tab {
        case .receive: return !viewModel.isLoading && !viewModel.offerUrl.isEmpty
        case .present: return viewModel.presentationPreviewActionEnabled && !viewModel.presentationRequestUrl.isEmpty
        case .credentials: return false
        }
    }

    var body: some View {
        let failed = viewModel.statusIsError(for: tab)
        WalletReviewScaffold(showsActions: failed && viewModel.presentationError == nil && canRetry) {
            if tab == .present, let error = viewModel.presentationError {
                PresentationErrorView(error: error, isEnabled: viewModel.presentationReviewEnabled,
                    onNotifyVerifier: viewModel.rejectPresentation, onDismiss: onClose)
            } else {
                if !failed { ProgressView() }
                Text(failed || viewModel.statusIsLoading(for: tab)
                    ? viewModel.statusMessage(for: tab) : "Preparing request…")
                    .accessibilityIdentifier("wallet.external.status")
            }
        } actions: {
            WalletActions(primary: WalletAction("Try again", enabled: canRetry,
                identifier: "wallet.external.retry", perform: onRetry))
        }
    }
}
