import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.walletDemoBranding) private var branding
    @FocusState private var inputFocused: Bool
    @State private var page: SetupPage
    @State private var mismatch = false

    init(viewModel: WalletViewModel, initialPage: SetupPage = .create) {
        self.viewModel = viewModel
        _page = State(initialValue: initialPage)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text(branding.appTitle).font(.title2.weight(.semibold))
                VStack(alignment: .leading, spacing: 8) {
                    Text(title).font(.largeTitle.weight(.bold))
                    Text(subtitle).foregroundColor(.secondary)
                }
                if !biometricChoice {
                    WalletSection {
                        SecureField(confirming ? "Confirm PIN" : "PIN", text: pinBinding)
                            .keyboardType(.numberPad)
                            .textFieldStyle(.roundedBorder)
                            .focused($inputFocused)
                            .disabled(viewModel.isAuthenticating)
                            .accessibilityIdentifier(confirming ? WalletAccessibilityID.pinConfirmationInput : WalletAccessibilityID.pinInput)
                            .padding(16)
                    }
                }
                if biometricChoice && viewModel.isBiometricUnlockAvailable && viewModel.useBiometrics && !viewModel.isAuthenticating {
                    Text("Biometrics enabled. Your PIN is always available as a fallback.")
                }
                if viewModel.isAuthenticating { ProgressView("Authenticating…") }
                if let error = mismatch ? "The PINs do not match. Try again." : viewModel.pinError {
                    Text(error).foregroundColor(.red)
                }
            }
            .frame(maxWidth: 640, alignment: .leading)
            .padding(20)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) {
            WalletActionBar(primary: primary, secondary: secondary)
        }
        .walletScrollDismissesKeyboard()
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { inputFocused = false }
            }
        }
        .onAppear { viewModel.refreshBiometricAvailability() }
    }

    private var isSetup: Bool { viewModel.auth == .setup }
    private var confirming: Bool { isSetup && page == .confirm }
    private var biometricChoice: Bool { isSetup && page == .biometrics }
    private var pinBinding: Binding<String> {
        Binding(get: { confirming ? viewModel.pinConfirmation : viewModel.pin }, set: { value in
            let digits = String(value.filter { $0 >= "0" && $0 <= "9" }.prefix(isSetup ? 6 : 8))
            guard digits != (confirming ? viewModel.pinConfirmation : viewModel.pin) else { return }
            mismatch = false
            viewModel.pinError = nil
            if confirming { viewModel.pinConfirmation = digits } else { viewModel.pin = digits }
        })
    }
    private var title: String {
        if !isSetup { return "Enter your PIN" }
        switch page {
        case .create: return "Create a PIN"
        case .confirm: return "Confirm your PIN"
        case .biometrics: return "A quicker way to unlock"
        }
    }
    private var subtitle: String {
        if !isSetup { return "Enter your PIN to unlock this wallet." }
        switch page {
        case .create: return "Choose six digits to unlock this wallet."
        case .confirm: return "Enter the same six digits again."
        case .biometrics:
            return viewModel.isBiometricUnlockAvailable
                ? "Use biometrics to open the app instead of typing the PIN. Signing approval is set up next."
                : "Biometrics are not available on this device."
        }
    }
    private var primary: WalletAction {
        let busy = viewModel.isAuthenticating
        if !isSetup {
            return WalletAction("Unlock", enabled: !busy, identifier: WalletAccessibilityID.pinSubmitButton) {
                inputFocused = false
                viewModel.submitPin()
            }
        }
        if biometricChoice {
            let label = !viewModel.isBiometricUnlockAvailable ? "Use PIN only"
                : viewModel.useBiometrics && !busy ? "Finish setup" : "Enable biometrics"
            return WalletAction(label, enabled: !busy, identifier: WalletAccessibilityID.pinSubmitButton) {
                if viewModel.isBiometricUnlockAvailable && !viewModel.useBiometrics {
                    viewModel.updateUseBiometrics(true)
                } else {
                    if !viewModel.isBiometricUnlockAvailable { viewModel.updateUseBiometrics(false) }
                    viewModel.submitPin()
                }
            }
        }
        return WalletAction("Continue", enabled: !busy && pinBinding.wrappedValue.count == 6,
            identifier: WalletAccessibilityID.pinSubmitButton) {
            inputFocused = false
            if confirming && viewModel.pin != viewModel.pinConfirmation { mismatch = true }
            else { mismatch = false; page = confirming ? .biometrics : .confirm }
        }
    }
    private var secondary: WalletAction? {
        let enabled = !viewModel.isAuthenticating
        if biometricChoice && viewModel.isBiometricUnlockAvailable {
            return WalletAction("Use PIN only", enabled: enabled, identifier: "wallet.pinSkipBiometrics") {
                viewModel.updateUseBiometrics(false)
                viewModel.submitPin()
            }
        }
        if isSetup && page != .create {
            return WalletAction("Back", enabled: enabled, identifier: "wallet.pinBack") {
                inputFocused = false
                mismatch = false
                page = page == .biometrics ? .confirm : .create
            }
        }
        if !isSetup && viewModel.isBiometricUnlockEnabled && viewModel.isBiometricUnlockAvailable {
            return WalletAction("Unlock with biometrics", enabled: enabled, identifier: WalletAccessibilityID.pinBiometricButton) {
                inputFocused = false
                viewModel.unlockWithBiometrics(force: true)
            }
        }
        return nil
    }
    enum SetupPage { case create, confirm, biometrics }
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
