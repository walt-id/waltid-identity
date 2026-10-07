import SwiftUI
import WalletDemoSharingUI

struct PinView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Environment(\.walletDemoBranding) private var branding
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var inputFocused = false
    @State private var focusRequest = 0
    @State private var previousStage = 0
    @State private var forward = true

    var body: some View {
        WalletAccessScaffold {
            Text(changing ? "Change PIN" : branding.appTitle).font(.title2.weight(.semibold))
            ZStack(alignment: .leading) {
                heading.id(stage)
                    .transition(WalletMotion.page(forward: forward, reduceMotion: reduceMotion))
            }
            .animation(WalletMotion.navigation(reduceMotion: reduceMotion), value: stage)
            .padding(.top, 24)
            .frame(minHeight: 120, alignment: .top)
            .clipped()
        } input: {
            WalletPinInput(value: pinBinding, label: confirming ? "Confirm PIN" : "PIN",
                digitCount: WalletViewModel.pinLength, isEnabled: !viewModel.isAuthenticating,
                isError: error != nil,
                identifier: confirming ? WalletAccessibilityID.pinConfirmationInput : WalletAccessibilityID.pinInput,
                focus: $inputFocused, onSubmit: { if value.count == WalletViewModel.pinLength { viewModel.submitPin() } })
        } feedback: {
            if viewModel.access.operation == .checkingPin || viewModel.access.operation == .savingPin {
                ProgressView().accessibilityLabel("Checking PIN")
            } else if let error {
                Text(error).font(.callout).foregroundStyle(.red).accessibilityAddTraits(.updatesFrequently)
            }
        } actions: {
            WalletActions(primary: retryAction,
                secondary: biometricAction ?? clearAction,
                tertiary: biometricAction != nil ? clearAction : backAction)
        }
        .task(id: scenePhase) {
            if scenePhase == .active && !changing {
                viewModel.refreshBiometricAvailability()
                viewModel.promptBiometricUnlockIfNeeded()
            }
        }
        .task(id: inputFocusRequest) {
            guard scenePhase == .active, !biometricActive else { inputFocused = false; return }
            inputFocused = true
        }
        .onAppear { previousStage = stage }
        .onChange(of: stage) { next in forward = next > previousStage; previousStage = next }
    }

    private var changing: Bool { viewModel.access.pinChange != nil }
    private var creating: Bool { viewModel.access.isCreatingPin }
    private var confirming: Bool { creating && viewModel.pinSetupStep == .confirm }
    private var value: String { confirming ? viewModel.pinConfirmation : viewModel.pin }
    private var error: String? { viewModel.pinError ?? viewModel.access.biometricOutcome?.fallbackMessage }
    private var biometricActive: Bool { viewModel.access.operation == .biometrics || viewModel.shouldPromptBiometricUnlock }
    private var stage: Int { creating ? (confirming ? 3 : 2) : (changing ? 1 : 0) }
    private struct FocusRequest: Equatable {
        let stage: Int
        let biometrics: Bool
        let active: Bool
        let request: Int
    }
    private var inputFocusRequest: FocusRequest {
        FocusRequest(stage: stage, biometrics: biometricActive, active: scenePhase == .active, request: focusRequest)
    }

    private var heading: some View {
        VStack(alignment: .leading, spacing: 8) {
            if creating {
                Text(confirming ? "Step 2 of 2" : "Step 1 of 2")
                    .font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
            }
            Text(creating ? (confirming ? "Confirm your PIN" : changing ? "Choose a new PIN" : "Choose a PIN")
                : changing ? "Enter your current PIN" : "Enter your PIN")
                .font(.largeTitle.weight(.bold))
        }
    }

    private var pinBinding: Binding<String> {
        Binding(get: { value }, set: { input in
            let digits = String(decoding: input.utf8.filter { (48...57).contains($0) }.prefix(WalletViewModel.pinLength), as: UTF8.self)
            if confirming { viewModel.updatePinConfirmation(digits) } else { viewModel.updatePin(digits) }
        })
    }

    private var clearAction: WalletAction {
        WalletAction("Clear", enabled: !viewModel.isAuthenticating && (!value.isEmpty || error != nil), identifier: "wallet.pinClearButton") {
            viewModel.clearPin(); focusRequest += 1
        }
    }
    private var retryAction: WalletAction? {
        guard case .retryPin = viewModel.access.operation else { return nil }
        return WalletAction("Try again", identifier: WalletAccessibilityID.pinSubmitButton, perform: viewModel.submitPin)
    }
    private var backAction: WalletAction? {
        if confirming || viewModel.access.pinChange == .newPin {
            return WalletAction("Back", enabled: !viewModel.isAuthenticating, identifier: "wallet.pinBackButton", perform: viewModel.editSetupPin)
        }
        return nil
    }
    private var biometricAction: WalletAction? {
        guard !changing, viewModel.auth == .login, viewModel.isBiometricUnlockEnabled, viewModel.isBiometricUnlockAvailable else { return nil }
        let label = switch viewModel.access.biometricKind {
        case .faceID: "Try Face ID"
        case .touchID: "Try Touch ID"
        case .generic: "Try biometrics"
        }
        return WalletAction(label, enabled: !viewModel.isAuthenticating, identifier: WalletAccessibilityID.pinBiometricButton) {
            inputFocused = false
            viewModel.unlockWithBiometrics(force: true)
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
