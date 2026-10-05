import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.walletDemoBranding) private var branding
    @Environment(\.scenePhase) private var scenePhase
    @State private var inputFocused = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(branding.appTitle).font(.title2.weight(.semibold))
                VStack(alignment: .leading, spacing: 8) {
                    if isSetup {
                        Text(confirming ? "Step 2 of 2" : "Step 1 of 2")
                            .font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
                    }
                    Text(isSetup ? (confirming ? "Confirm your PIN" : "Choose a PIN") : "Enter your PIN")
                        .font(.largeTitle.weight(.bold))
                }
                VStack(spacing: 8) {
                    WalletPinInput(value: pinBinding, label: confirming ? "Confirm PIN" : "PIN",
                        digitCount: isSetup ? WalletViewModel.setupPinLength : max(WalletViewModel.setupPinLength, viewModel.pin.count),
                        isEnabled: !viewModel.isAuthenticating, isError: viewModel.pinError != nil,
                        identifier: confirming ? WalletAccessibilityID.pinConfirmationInput : WalletAccessibilityID.pinInput,
                        focus: $inputFocused, onSubmit: { inputFocused = false })
                    if !isSetup { Text("Existing PINs with 4–8 digits are accepted.").font(.footnote).foregroundStyle(.secondary) }
                }
                Text(isSetup ? (confirming ? "Enter the same six digits again." : "Use six digits to protect your wallet.")
                     : "Enter your PIN to unlock this wallet.").foregroundStyle(.secondary)
                if confirming && viewModel.isBiometricUnlockAvailable {
                    Text("Next, your device will offer biometric unlock. Decline to keep using your PIN.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if viewModel.isAuthenticating { ProgressView("Authenticating…") }
                if let error = viewModel.pinError { Text(error).foregroundStyle(.red).accessibilityAddTraits(.updatesFrequently) }
            }
            .frame(maxWidth: 640, alignment: .leading)
            .padding(20)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) {
            WalletActions(primary: primary, secondary: secondary ?? keyboardAction,
                tertiary: secondary == nil ? nil : keyboardAction)
                .padding(.horizontal, 20).padding(.vertical, 12).background(.regularMaterial)
        }
        .walletScrollDismissesKeyboard()
        .onAppear {
            viewModel.refreshBiometricAvailability()
            viewModel.promptBiometricUnlockIfNeeded()
        }
        .task(id: inputFocusRequest) {
            let request = inputFocusRequest
            guard request.isActive, request.target != .none else { inputFocused = false; return }
            guard !Task.isCancelled, inputFocusRequest == request else { return }
            inputFocused = true
        }
    }

    private var isSetup: Bool { viewModel.auth == .setup }
    private var confirming: Bool { isSetup && viewModel.pinSetupStep == .confirm }
    private var value: String { confirming ? viewModel.pinConfirmation : viewModel.pin }
    private enum InputFocusTarget: Hashable { case none, choose, confirm, unlock }
    private struct InputFocusRequest: Equatable {
        let target: InputFocusTarget
        let isActive: Bool
    }
    private var inputFocusRequest: InputFocusRequest {
        InputFocusRequest(target: inputFocusTarget, isActive: scenePhase == .active)
    }
    private var inputFocusTarget: InputFocusTarget {
        guard !viewModel.isAuthenticating, !viewModel.shouldPromptBiometricUnlock else { return .none }
        switch viewModel.auth {
        case .setup: return confirming ? .confirm : .choose
        case .login: return .unlock
        case .storageUnavailable, .unlocked: return .none
        }
    }

    private var pinBinding: Binding<String> {
        Binding(get: { value }, set: { input in
            let digits = isSetup
                ? String(decoding: input.utf8.filter { (48...57).contains($0) }.prefix(WalletViewModel.setupPinLength), as: UTF8.self)
                : String(input.filter(\.isNumber).prefix(8))
            guard digits != value else { return }
            if confirming { viewModel.updatePinConfirmation(digits) } else { viewModel.updatePin(digits) }
        })
    }

    private var primary: WalletAction {
        WalletAction(isSetup ? (confirming ? "Confirm PIN" : "Continue") : "Unlock",
            enabled: !viewModel.isAuthenticating && (isSetup ? value.count == WalletViewModel.setupPinLength : (4...8).contains(value.count)),
            identifier: WalletAccessibilityID.pinSubmitButton) {
            inputFocused = false
            viewModel.submitPin()
        }
    }

    private var keyboardAction: WalletAction? {
        guard inputFocused else { return nil }
        return WalletAction("Done", enabled: !viewModel.isAuthenticating, identifier: "wallet.pinKeyboardAction") {
            inputFocused = false
        }
    }

    private var secondary: WalletAction? {
        if confirming {
            return WalletAction("Back", enabled: !viewModel.isAuthenticating, identifier: "wallet.pinBackButton") {
                inputFocused = false
                viewModel.editSetupPin()
            }
        }
        guard !isSetup && viewModel.isBiometricUnlockEnabled && viewModel.isBiometricUnlockAvailable else { return nil }
        return WalletAction("Unlock with biometrics", enabled: !viewModel.isAuthenticating,
                            identifier: WalletAccessibilityID.pinBiometricButton) {
            inputFocused = false
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
