import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel
    @FocusState private var focusedField: Field?

    var body: some View {
        ScrollView {
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
                    .focused($focusedField, equals: .pin)
                    .accessibilityIdentifier(WalletAccessibilityID.pinInput)

                if isSetup {
                    setupExtras
                }

                if let error = viewModel.pinError {
                    errorText(error, identifier: nil)
                }
                if isSetup, let error = viewModel.signingProtectionError {
                    errorText(error, identifier: WalletAccessibilityID.signingProtectionError)
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
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
        }
        .background {
            Color.clear
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
            return "Use 4 to 8 digits for this local demo unlock flow."
        }
        return "Unlock the local demo wallet."
    }

    private var shouldShowBiometricUnlockButton: Bool {
        !isSetup && viewModel.isBiometricUnlockEnabled && viewModel.isBiometricUnlockAvailable
    }

    private var canSubmit: Bool {
        !viewModel.isAuthenticating && (
            !isSetup ||
                viewModel.selectedSigningProtection != .biometric ||
                viewModel.isBiometricSigningAvailable
        )
    }

    @ViewBuilder
    private var setupExtras: some View {
        SecureField("Confirm PIN", text: $viewModel.pinConfirmation)
            .keyboardType(.numberPad)
            .textFieldStyle(.roundedBorder)
            .focused($focusedField, equals: .confirmation)
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

        Text("Signing protection")
            .font(.title3.weight(.semibold))
        Text("Choose how wallet signing is protected. Changing it later creates a new wallet key and DID.")
            .font(.footnote)
            .foregroundStyle(.secondary)

        switch viewModel.signingProtectionMode {
        case .optional:
            signingProtectionChoice(.biometric)
            signingProtectionChoice(.none)
        case .required, .disabled:
            signingProtectionChoice(viewModel.signingProtectionMode.defaultSelection, managed: true)
            Text("Managed by app configuration.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }

        if viewModel.selectedSigningProtection == .biometric,
           !viewModel.isBiometricSigningAvailable {
            Text(
                viewModel.biometricSigningAvailability?.message
                    ?? "Checking strong biometric availability..."
            )
            .font(.footnote)
            .foregroundStyle(
                viewModel.biometricSigningAvailability == nil ? Color.secondary : Color.red
            )
            .accessibilityIdentifier(WalletAccessibilityID.signingProtectionAvailability)
        }
    }

    private var biometricsHelpText: String {
        if viewModel.isBiometricUnlockAvailable {
            return "Use Face ID or fingerprint instead of typing the PIN. The PIN remains a fallback."
        }
        return "Biometrics are not available on this device."
    }

    private func signingProtectionChoice(
        _ protection: WalletDemoSigningProtection,
        managed: Bool = false
    ) -> some View {
        SigningProtectionChoiceView(
            protection: protection,
            selected: viewModel.selectedSigningProtection == protection,
            enabled: !managed &&
                !viewModel.isAuthenticating &&
                (protection != .biometric || viewModel.isBiometricSigningAvailable),
            action: { viewModel.selectSigningProtection(protection) }
        )
        .accessibilityIdentifier(
            protection == .biometric
                ? WalletAccessibilityID.signingProtectionBiometric
                : WalletAccessibilityID.signingProtectionNone
        )
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
