import SwiftUI
import WalletDemoSharingUI

struct WalletTabStatusBanner: View {
    @ObservedObject var viewModel: WalletViewModel
    let tab: WalletTab

    var body: some View {
        if viewModel.isStatusVisible(for: tab) {
            StatusBannerView(
                message: viewModel.statusMessage(for: tab),
                isLoading: viewModel.statusIsLoading(for: tab),
                isError: viewModel.statusIsError(for: tab),
                isExpanded: viewModel.statusExpanded,
                onDismiss: dismissAction,
                onToggleExpanded: viewModel.statusKind(for: tab) == .error ? viewModel.toggleStatusExpanded : nil
            )
        }
    }

    private var dismissAction: (() -> Void)? {
        switch viewModel.statusKind(for: tab) {
        case .success, .error:
            return viewModel.dismissStatus
        case .busy, .info, nil:
            return nil
        }
    }
}

extension View {
    func walletSettingsToolbar(viewModel: WalletViewModel) -> some View {
        toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(destination: SettingsView(viewModel: viewModel)) {
                    Image(systemName: "gearshape")
                }
                .accessibilityIdentifier(WalletAccessibilityID.settingsButton)
            }
        }
    }
}
