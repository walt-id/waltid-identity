import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel

    private var isSetup: Bool {
        if case .setup = viewModel.auth { return true }
        return false
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("walt.id Wallet")
                .font(.largeTitle.bold())
            Text(isSetup ? "Create a PIN" : "Enter your PIN")
                .font(.title3.weight(.semibold))
            Text(
                isSetup
                    ? "Use 4 to 8 digits for this local demo unlock flow."
                    : "Unlock the local demo wallet."
            )
            .foregroundStyle(.secondary)

            SecureField("PIN", text: $viewModel.pin)
                .textContentType(.oneTimeCode)
                .keyboardType(.numberPad)
                .textFieldStyle(.roundedBorder)
                .accessibilityIdentifier(WalletAccessibilityID.pinInput)

            if isSetup {
                SecureField("Confirm PIN", text: $viewModel.pinConfirmation)
                    .textContentType(.oneTimeCode)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
                    .accessibilityIdentifier(WalletAccessibilityID.pinConfirmationInput)

                Toggle(isOn: $viewModel.useBiometrics) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Unlock with biometrics")
                        Text(
                            viewModel.isBiometricUnlockAvailable
                                ? "Use Face ID or fingerprint instead of typing the PIN. The PIN remains a fallback."
                                : "Biometrics are not available on this device."
                        )
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    }
                }
                .disabled(!viewModel.isBiometricUnlockAvailable)
                .accessibilityIdentifier(WalletAccessibilityID.pinBiometricToggle)
            }

            if let error = viewModel.pinError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }

            Button(isSetup ? "Set PIN" : "Unlock") {
                viewModel.submitPin()
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isAuthenticating)
            .accessibilityIdentifier(WalletAccessibilityID.pinSubmitButton)

            if !isSetup, viewModel.isBiometricUnlockEnabled, viewModel.isBiometricUnlockAvailable {
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
            if case .login = viewModel.auth {
                viewModel.unlockWithBiometrics()
            }
        }
    }
}
