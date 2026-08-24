import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("walt.id Wallet")
                .font(.largeTitle.weight(.bold))
            Text(title)
                .font(.title3.weight(.semibold))
            Text(subtitle)
                .foregroundColor(.secondary)

            SecureField("PIN", text: $viewModel.pin)
                .keyboardType(.numberPad)
                .textFieldStyle(.roundedBorder)
                .accessibilityIdentifier(WalletAccessibilityID.pinInput)

            if isSetup {
                setupExtras
            }

            if let error = viewModel.pinError {
                Text(error)
                    .font(.footnote)
                    .foregroundColor(.red)
            }

            Button {
                viewModel.submitPin()
            } label: {
                Text(isSetup ? "Set PIN" : "Unlock")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isAuthenticating)
            .accessibilityIdentifier(WalletAccessibilityID.pinSubmitButton)

            if shouldShowBiometricUnlockButton {
                Button("Unlock with biometrics") {
                    viewModel.unlockWithBiometrics(force: true)
                }
                .disabled(viewModel.isAuthenticating)
                .accessibilityIdentifier(WalletAccessibilityID.pinBiometricButton)
            }

            Spacer()
        }
        .padding(24)
        .onAppear {
            promptBiometricsIfSceneActive()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                promptBiometricsIfSceneActive()
            }
        }
    }

    private func promptBiometricsIfSceneActive() {
        guard scenePhase == .active else { return }
        viewModel.refreshBiometricAvailability()
        viewModel.promptBiometricUnlockIfNeeded()
    }

    private var isSetup: Bool {
        viewModel.auth == .setup
    }

    private var title: String {
        isSetup ? "Create a PIN" : "Enter your PIN"
    }

    private var subtitle: String {
        if isSetup {
            return "Use 4 to 8 digits for this local demo unlock flow."
        }
        return "Unlock the local demo wallet."
    }

    private var shouldShowBiometricUnlockButton: Bool {
        !isSetup && viewModel.isBiometricUnlockEnabled && viewModel.isBiometricUnlockAvailable
    }

    @ViewBuilder
    private var setupExtras: some View {
        SecureField("Confirm PIN", text: $viewModel.pinConfirmation)
            .keyboardType(.numberPad)
            .textFieldStyle(.roundedBorder)
            .accessibilityIdentifier(WalletAccessibilityID.pinConfirmationInput)

        Toggle(
            "Unlock with biometrics",
            isOn: Binding(
                get: { viewModel.useBiometrics },
                set: { viewModel.updateUseBiometrics($0) }
            )
        )
        .disabled(!viewModel.isBiometricUnlockAvailable || viewModel.isAuthenticating)
        .accessibilityIdentifier(WalletAccessibilityID.pinBiometricToggle)

        Text(biometricsHelpText)
            .font(.footnote)
            .foregroundColor(.secondary)
    }

    private var biometricsHelpText: String {
        if viewModel.isBiometricUnlockAvailable {
            return "Use Face ID or fingerprint instead of typing the PIN. The PIN remains a fallback."
        }
        return "Biometrics are not available on this device."
    }
}
