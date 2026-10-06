import SwiftUI
import WalletDemoSharingUI

struct BiometricSetupView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Unlock with biometrics").font(.largeTitle.weight(.bold))
                Text(explanation).foregroundStyle(.secondary)
            }
            .frame(maxWidth: 640, alignment: .leading).padding(20)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) {
            WalletFooter {
                if viewModel.isAuthenticating { ProgressView("Authenticating…").frame(maxWidth: .infinity, alignment: .leading) }
                WalletActions(primary: viewModel.isBiometricUnlockAvailable ? retry : continueWithPIN,
                    secondary: viewModel.isBiometricUnlockAvailable ? continueWithPIN : nil)
            }
        }
        .accessibilityIdentifier("wallet.biometricSetup")
        .onAppear { viewModel.refreshBiometricAvailability() }
    }

    private var explanation: String {
        guard case .biometricSetup(let outcome) = viewModel.auth else { return "" }
        switch outcome {
        case .cancelled: return "Setup was cancelled. You can try again or continue with your PIN."
        case .unavailable: return "Biometrics are unavailable. You can continue with your PIN."
        case .lockedOut: return "Biometrics are temporarily locked. Try again when available, or continue with your PIN."
        case .failed: return "Biometric setup did not finish. You can try again or continue with your PIN."
        default: return "Your PIN is ready. Use biometrics for quicker unlock, or continue with your PIN."
        }
    }

    private var retry: WalletAction {
        WalletAction("Try again", enabled: !viewModel.isAuthenticating,
            identifier: "wallet.biometricSetupRetry", perform: viewModel.retryBiometricSetup)
    }

    private var continueWithPIN: WalletAction {
        WalletAction("Use PIN only", enabled: !viewModel.isAuthenticating,
            identifier: "wallet.biometricSetupContinue", perform: viewModel.continueWithoutBiometrics)
    }
}
