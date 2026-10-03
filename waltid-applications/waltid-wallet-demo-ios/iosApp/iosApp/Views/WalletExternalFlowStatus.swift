import SwiftUI
import WalletDemoSharingUI

struct WalletExternalFlowStatus: View {
    @ObservedObject var viewModel: WalletViewModel
    let onRetry: () -> Void

    var body: some View {
        WalletReviewScaffold(showsActions: viewModel.isError && viewModel.presentationError == nil) {
            if let error = viewModel.presentationError {
                PresentationErrorView(error: error, isEnabled: viewModel.presentationReviewEnabled,
                    onNotifyVerifier: viewModel.rejectPresentation, onDismiss: viewModel.cancelPresentationReview)
            } else {
                if viewModel.isLoading { ProgressView() }
                Text(viewModel.statusMessage).accessibilityIdentifier("wallet.external.status")
            }
        } actions: {
            WalletActions(primary: WalletAction("Try again", identifier: "wallet.external.retry", perform: onRetry))
        }
    }
}
