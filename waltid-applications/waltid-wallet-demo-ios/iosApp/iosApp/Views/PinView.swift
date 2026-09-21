import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel
    @FocusState private var focusedField: Field?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("walt.id Wallet")
                    .font(.largeTitle.weight(.bold))
                Text(title)
                    .font(.title3.weight(.semibold))
                Text(subtitle)
                    .foregroundColor(.secondary)

                VStack(spacing: 16) {
                    SecureField("PIN", text: $viewModel.pin)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                        .focused($focusedField, equals: .pin)
                        .accessibilityIdentifier(WalletAccessibilityID.pinInput)
                    if isSetup {
                        SecureField("Confirm PIN", text: $viewModel.pinConfirmation)
                            .keyboardType(.numberPad)
                            .textFieldStyle(.roundedBorder)
                            .focused($focusedField, equals: .confirmation)
                            .accessibilityIdentifier(WalletAccessibilityID.pinConfirmationInput)
                    }
                }
                .padding(16)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                if isSetup { setupExtras }

                if let error = viewModel.pinError {
                    errorText(error, identifier: nil)
                }

                Button {
                    focusedField = nil
                    viewModel.submitPin()
                } label: {
                    Text(isSetup ? "Set PIN" : "Unlock")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!canSubmit)
                .accessibilityIdentifier(WalletAccessibilityID.pinSubmitButton)

                if shouldShowBiometricUnlockButton {
                    Button("Unlock with biometrics") {
                        focusedField = nil
                        viewModel.unlockWithBiometrics(force: true)
                    }
                    .disabled(viewModel.isAuthenticating)
                    .accessibilityIdentifier(WalletAccessibilityID.pinBiometricButton)
                }

                Spacer(minLength: 12)
            }
            .frame(maxWidth: 640, alignment: .leading)
            .padding(20)
            .frame(maxWidth: .infinity)
        }
        .background {
            Color(uiColor: .systemGroupedBackground)
                .contentShape(Rectangle())
                .onTapGesture {
                    focusedField = nil
                }
        }
        .walletScrollDismissesKeyboard()
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focusedField = nil }
            }
        }
        .onAppear {
            viewModel.refreshBiometricAvailability()
        }
    }

    private var isSetup: Bool {
        viewModel.auth == .setup
    }

    private var title: String {
        isSetup ? "Create a PIN" : "Enter your PIN"
    }

    private var subtitle: String {
        if isSetup {
            return "Use 4 to 8 digits to unlock this wallet."
        }
        return "Enter your PIN to unlock this wallet."
    }

    private var shouldShowBiometricUnlockButton: Bool {
        !isSetup && viewModel.isBiometricUnlockEnabled && viewModel.isBiometricUnlockAvailable
    }

    private var canSubmit: Bool {
        !viewModel.isAuthenticating
    }

    @ViewBuilder
    private var setupExtras: some View {
        VStack(alignment: .leading, spacing: 8) {
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
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private var biometricsHelpText: String {
        if viewModel.isBiometricUnlockAvailable {
            return "Use biometrics to open the app instead of typing the PIN. Signing approval is set up next."
        }
        return "Biometrics are not available on this device."
    }

    @ViewBuilder
    private func errorText(_ message: String, identifier: String?) -> some View {
        let text = Text(message)
            .font(.footnote)
            .foregroundColor(.red)
        if let identifier {
            text.accessibilityIdentifier(identifier)
        } else {
            text
        }
    }

    private enum Field: Hashable {
        case pin
        case confirmation
    }
}

private extension View {
    @ViewBuilder
    func walletScrollDismissesKeyboard() -> some View {
        if #available(iOS 16.0, *) {
            scrollDismissesKeyboard(.interactively)
        } else {
            self
        }
    }
}

struct SigningProtectionChoiceView: View {
    let protection: WalletDemoSigningProtection
    let selected: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(enabled ? Color.accentColor : Color.secondary)
                VStack(alignment: .leading, spacing: 4) {
                    Text(protection.title)
                        .foregroundStyle(.primary)
                    Text(protection.explanation)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.6)
    }
}
