import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.walletDemoBranding) private var branding
    @FocusState private var focusedInput: Input?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(branding.appTitle).font(.title2.weight(.semibold))
                VStack(alignment: .leading, spacing: 8) {
                    Text(isSetup ? "Create a PIN" : "Enter your PIN").font(.largeTitle.weight(.bold))
                    Text(isSetup ? "Choose six digits and enter them again to confirm."
                         : "Enter your PIN to unlock this wallet.").foregroundColor(.secondary)
                }
                WalletSection {
                    VStack(alignment: .leading, spacing: 12) {
                        pinInput(.pin, label: "PIN", identifier: WalletAccessibilityID.pinInput)
                        if isSetup {
                            pinInput(.confirmation, label: "Confirm PIN", identifier: WalletAccessibilityID.pinConfirmationInput)
                        }
                    }
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
                    .disabled(viewModel.isAuthenticating)
                    .padding(16)
                }
                if isSetup {
                    WalletSection {
                        Toggle(isOn: Binding(get: { viewModel.useBiometrics }, set: { enabled in
                            focusedInput = nil
                            viewModel.updateUseBiometrics(enabled)
                        })) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Use biometric unlock")
                                Text(biometricDescription).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                        .disabled(!viewModel.isBiometricUnlockAvailable || viewModel.isAuthenticating)
                        .accessibilityIdentifier(WalletAccessibilityID.pinBiometricToggle)
                        .padding(16)
                    }
                }
                if viewModel.isAuthenticating { ProgressView("Authenticating…") }
                if let error = viewModel.pinError {
                    Text(error).foregroundColor(.red)
                }
            }
            .frame(maxWidth: 640, alignment: .leading)
            .padding(20)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) {
            // Keep numeric-keyboard navigation in the same inset as confirmation.
            // A floating system keyboard toolbar can overlap a separate bottom action bar.
            WalletActions(primary: primary, secondary: keyboardAction ?? secondary,
                tertiary: keyboardAction == nil ? nil : secondary)
                .padding(.horizontal, 20).padding(.vertical, 12).background(.regularMaterial)
        }
        .walletScrollDismissesKeyboard()
        .onAppear { viewModel.refreshBiometricAvailability() }
    }

    private var isSetup: Bool { viewModel.auth == .setup }
    private enum Input { case pin, confirmation }

    private func pinInput(_ input: Input, label: LocalizedStringKey, identifier: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.caption).foregroundStyle(.secondary).accessibilityHidden(true)
            SecureField(label, text: pinBinding(for: input))
                .focused($focusedInput, equals: input)
                .onSubmit { focusedInput = nil }
                .accessibilityIdentifier(identifier)
        }
    }

    private func pinBinding(for input: Input) -> Binding<String> {
        Binding(get: { input == .confirmation ? viewModel.pinConfirmation : viewModel.pin }, set: { value in
            let digits = String(value.filter { $0 >= "0" && $0 <= "9" }.prefix(isSetup ? 6 : 8))
            guard digits != (input == .confirmation ? viewModel.pinConfirmation : viewModel.pin) else { return }
            viewModel.pinError = nil
            if input == .confirmation { viewModel.pinConfirmation = digits } else { viewModel.pin = digits }
        })
    }

    private var biometricDescription: String {
        if !viewModel.isBiometricUnlockAvailable { return "Biometrics are not available on this device." }
        if viewModel.useBiometrics && !viewModel.isAuthenticating {
            return "Biometrics enabled. Your PIN is always available as a fallback."
        }
        return "Use biometrics to open the app instead of typing the PIN. Signing approval is set up next."
    }

    private var primary: WalletAction {
        WalletAction(isSetup ? "Create PIN" : "Unlock",
            enabled: !viewModel.isAuthenticating && (!isSetup || (viewModel.pin.count == 6 && viewModel.pinConfirmation.count == 6)),
            identifier: WalletAccessibilityID.pinSubmitButton) {
            focusedInput = nil
            viewModel.submitPin()
        }
    }

    private var keyboardAction: WalletAction? {
        guard let focusedInput else { return nil }
        let next = isSetup && focusedInput == .pin
        return WalletAction(next ? "Next" : "Done", enabled: !viewModel.isAuthenticating,
            identifier: "wallet.pinKeyboardAction") {
            self.focusedInput = next ? .confirmation : nil
        }
    }

    private var secondary: WalletAction? {
        guard !isSetup && viewModel.isBiometricUnlockEnabled && viewModel.isBiometricUnlockAvailable else { return nil }
        return WalletAction("Unlock with biometrics", enabled: !viewModel.isAuthenticating,
                            identifier: WalletAccessibilityID.pinBiometricButton) {
            focusedInput = nil
            viewModel.unlockWithBiometrics(force: true)
        }
    }
}

private extension View {
    @ViewBuilder
    func walletScrollDismissesKeyboard() -> some View {
        if #available(iOS 16.0, *) { scrollDismissesKeyboard(.interactively) } else { self }
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
