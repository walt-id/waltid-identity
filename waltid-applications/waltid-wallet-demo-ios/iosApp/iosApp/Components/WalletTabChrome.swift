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
                onToggleExpanded: expandAction
            )
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

extension View {
    func walletFlowToolbar(onBack: (() -> Void)?, backEnabled: Bool, onOpenSettings: @escaping () -> Void) -> some View {
        walletSettingsToolbar(onOpenSettings: onOpenSettings).toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                if let onBack {
                    Button(action: onBack) { Label("Back to wallet", systemImage: "chevron.backward") }
                        .disabled(!backEnabled)
                        .accessibilityIdentifier("wallet.flowBack")
                }
            }
        }
    }

    func walletSettingsToolbar(onOpenSettings: @escaping () -> Void) -> some View {
        toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel("Settings")
                .accessibilityIdentifier(WalletAccessibilityID.settingsButton)
            }
        }
    }
}
