import SwiftUI
import WalletDemoSharingUI

struct ContentView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        Group {
            switch viewModel.auth {
            case .setup, .login:
                PinView(viewModel: viewModel)
            case .storageUnavailable(let message):
                pinStorageUnavailable(message)
            case .unlocked:
                HomeView(viewModel: viewModel)
            }
        }
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

#if DEBUG
#Preview {
    ContentView(viewModel: WalletViewModel.mockForUITests())
}
#endif
