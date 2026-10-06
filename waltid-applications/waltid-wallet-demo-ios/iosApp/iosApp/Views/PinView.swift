import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.walletDemoBranding) private var branding
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var inputFocused = false

    var body: some View {
        ScrollView {
            ZStack {
                pinContent(confirming: confirming)
                    .id(confirming)
                    .transition(WalletMotion.page(forward: confirming, reduceMotion: reduceMotion))
            }
            .animation(WalletMotion.navigation(reduceMotion: reduceMotion), value: confirming)
            .frame(maxWidth: 640, alignment: .leading)
            .clipped()
            .padding(20)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) {
            WalletFooter {
                if viewModel.isAuthenticating { ProgressView("Authenticating…").frame(maxWidth: .infinity, alignment: .leading) }
                else if let error = viewModel.pinError {
                    Text(error).font(.callout).foregroundStyle(.red).frame(maxWidth: .infinity, alignment: .leading).accessibilityAddTraits(.updatesFrequently)
                }
                WalletActions(primary: primary, secondary: secondary)
            }
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

    private var isSetup: Bool { viewModel.auth == .setup || confirming }
    // Finishing setup removes this view; it must not start a reverse page transition
    // and recreate the Choose editor while the sheet is dismissing its keyboard.
    private var confirming: Bool { viewModel.pinSetupStep == .confirm && viewModel.auth != .login }
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
        case .storageUnavailable, .biometricSetup, .unlocked: return .none
        }
    }

    private func pinContent(confirming displayedConfirm: Bool) -> some View {
        let active = displayedConfirm == confirming
        return VStack(alignment: .leading, spacing: 20) {
            Text(branding.appTitle).font(.title2.weight(.semibold))
            VStack(alignment: .leading, spacing: 8) {
                if isSetup {
                    Text(displayedConfirm ? "Step 2 of 2" : "Step 1 of 2")
                        .font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
                }
                Text(isSetup ? (displayedConfirm ? "Confirm your PIN" : "Choose a PIN") : "Enter your PIN")
                    .font(.largeTitle.weight(.bold))
            }
            WalletPinInput(value: pinBinding(confirming: displayedConfirm), label: displayedConfirm ? "Confirm PIN" : "PIN",
                digitCount: WalletViewModel.pinLength, isEnabled: active && !viewModel.isAuthenticating,
                isError: active && viewModel.pinError != nil,
                identifier: displayedConfirm ? WalletAccessibilityID.pinConfirmationInput : WalletAccessibilityID.pinInput,
                focus: Binding(get: { displayedConfirm == confirming && inputFocused }, set: { if displayedConfirm == confirming { inputFocused = $0 } }),
                onSubmit: { if displayedConfirm == confirming { inputFocused = false } })
        }
        .accessibilityHidden(!active)
    }

    private func pinBinding(confirming displayedConfirm: Bool) -> Binding<String> {
        Binding(get: { displayedConfirm ? viewModel.pinConfirmation : viewModel.pin }, set: { input in
            guard displayedConfirm == confirming else { return }
            let digits = String(decoding: input.utf8.filter { (48...57).contains($0) }.prefix(WalletViewModel.pinLength), as: UTF8.self)
            guard digits != (displayedConfirm ? viewModel.pinConfirmation : viewModel.pin) else { return }
            if displayedConfirm { viewModel.updatePinConfirmation(digits) } else { viewModel.updatePin(digits) }
        })
    }

    private var primary: WalletAction {
        WalletAction(isSetup ? (confirming ? "Confirm PIN" : "Continue") : "Unlock",
            enabled: !viewModel.isAuthenticating && value.count == WalletViewModel.pinLength,
            identifier: WalletAccessibilityID.pinSubmitButton) {
            inputFocused = false
            viewModel.submitPin()
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
