import SwiftUI
import WalletDemoSharingUI

struct WalletTabStatusBanner: View {
    @ObservedObject var viewModel: WalletViewModel
    let tab: WalletTab

    var isVisible: Bool {
        guard viewModel.isStatusVisible(for: tab), let kind = viewModel.statusKind(for: tab) else { return false }
        return tab == .credentials || kind == .busy || kind == .error
    }

    var body: some View {
        if isVisible {
            StatusBannerView(
                message: viewModel.statusMessage(for: tab),
                isLoading: viewModel.statusIsLoading(for: tab),
                isError: viewModel.statusIsError(for: tab),
                isExpanded: viewModel.statusExpanded,
                onDismiss: dismissAction,
                onToggleExpanded: expandAction
            )
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("wallet.status.feedback")
        }
    }

    private var dismissAction: (() -> Void)? {
        switch viewModel.statusKind(for: tab) {
        case .success, .error:
            return { viewModel.dismissStatus() }
        case .busy, .info, nil:
            return nil
        }
    }

    private var expandAction: (() -> Void)? {
        guard viewModel.statusKind(for: tab) == .error else { return nil }
        return { viewModel.toggleStatusExpanded() }
    }
}

/// Feedback without actions uses the same measured safe-area footer as review controls.
struct WalletTabFeedback: View {
    @ObservedObject var viewModel: WalletViewModel
    let tab: WalletTab

    var body: some View {
        let banner = WalletTabStatusBanner(viewModel: viewModel, tab: tab)
        if banner.isVisible { WalletFooter { banner } }
    }
}

extension View {
    func walletFlowToolbar(onBack: (() -> Void)?, backEnabled: Bool, external: Bool = false, closing: Bool = false) -> some View {
        toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let onBack {
                    Button(action: onBack) {
                        WalletToolbarIcon("xmark", isBusy: closing)
                    }
                        .accessibilityLabel(closing ? "Closing the secure connection…" : "Close request")
                        .disabled(!backEnabled)
                        .accessibilityIdentifier(external ? "wallet.external.close" : "wallet.flowBack")
                }
            }
        }
    }

}
