import SwiftUI
import WalletDemoSharingUI

struct ContentView: View {
    @ObservedObject var viewModel: WalletViewModel
    var body: some View {
        walletContent
        .overlay(alignment: .topTrailing) {
            if viewModel.externalFlow != nil && (viewModel.auth != .unlocked || !viewModel.isReady) {
                Button { viewModel.closeExternalFlow() } label: { WalletToolbarIcon("xmark") }
                    .accessibilityLabel("Close pending request")
                    .disabled(!viewModel.canDismissExternalFlow)
                    .accessibilityIdentifier("wallet.external.close")
                    .padding(16)
            }
        }
        .task { viewModel.prepareExternalFlow() }
        .onChange(of: viewModel.externalFlow) { _ in viewModel.prepareExternalFlow() }
        .onChange(of: viewModel.isReady) { _ in viewModel.prepareExternalFlow() }
        .onChange(of: viewModel.isLoading) { _ in viewModel.prepareExternalFlow() }
        .onChange(of: viewModel.auth) { _ in viewModel.prepareExternalFlow() }
        .alert("Request already in progress", isPresented: Binding(
            get: { viewModel.incomingLinkNotice != nil }, set: { if !$0 { viewModel.incomingLinkNotice = nil } })) {
            Button("OK") { viewModel.incomingLinkNotice = nil }
        } message: { Text(viewModel.incomingLinkNotice ?? "") }
        .alert(
            "Biometric signing unavailable",
            isPresented: Binding(
                get: { viewModel.signingProtectionWarning != nil },
                set: { isPresented in
                    if !isPresented {
                        viewModel.dismissSigningProtectionWarning()
                    }
                }
            )
        ) {
            Button("OK") {
                viewModel.dismissSigningProtectionWarning()
            }
            .accessibilityIdentifier(WalletAccessibilityID.signingProtectionWarningDismiss)
        } message: {
            Text(viewModel.signingProtectionWarning ?? "")
                .accessibilityIdentifier(WalletAccessibilityID.signingProtectionWarning)
        }
    }

    private var walletContent: some View {
        Group {
            switch viewModel.auth {
            case .setup, .login:
                PinView(viewModel: viewModel)
            case .biometricSetup:
                BiometricSetupView(viewModel: viewModel)
            case .storageUnavailable(let message):
                pinStorageUnavailable(message)
            case .unlocked:
                HomeView(viewModel: viewModel)
            }
        }
    }

    private func pinStorageUnavailable(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("walt.id Wallet")
                .font(.largeTitle.weight(.bold))
            Text("PIN storage unavailable")
                .font(.title3.weight(.semibold))
            Text("\(message). The wallet remains locked.")
                .foregroundColor(.red)
            Spacer()
        }
        .padding(24)
    }
}

// Preview the side-effect-free components in WalletDemoSharingUI. App-host
// previews must not initialize PIN storage, reader stores or registration services.
