import SwiftUI
import WalletDemoSharingUI

struct WalletAccessSettingsView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        Group {
            if viewModel.access.pinChange != nil { PinView(viewModel: viewModel) }
            else {
                List {
                    Section {
                        Button("Change PIN", action: viewModel.startPinChange)
                            .accessibilityIdentifier("wallet.settingsChangePin")
                            .disabled(viewModel.isAuthenticating)
                        Toggle(biometricTitle, isOn: Binding(get: { viewModel.isBiometricUnlockEnabled }, set: viewModel.setBiometricUnlockEnabled))
                            .accessibilityIdentifier("wallet.settingsBiometricUnlock")
                            .disabled(viewModel.isAuthenticating || (!viewModel.isBiometricUnlockAvailable && !viewModel.isBiometricUnlockEnabled))
                    } footer: {
                        if !viewModel.isBiometricUnlockAvailable {
                            BiometricRecoverySection(availability: viewModel.access.biometricAvailability, kind: viewModel.access.biometricKind)
                        }
                    }
                    if let notice = viewModel.access.settingsNotice {
                        Section {
                            Text(notice.message).foregroundStyle(notice.isError ? Color.red : Color.secondary)
                                .accessibilityIdentifier("wallet.accessNotice")
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .frame(maxWidth: 640).frame(maxWidth: .infinity)
            }
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle(viewModel.access.pinChange == nil ? "Wallet access" : "")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(viewModel.access.pinChange != nil)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if viewModel.access.pinChange != nil {
                    Button(action: viewModel.cancelPinChange) { Image(systemName: "xmark") }
                        .accessibilityLabel("Cancel PIN change")
                        .accessibilityIdentifier("wallet.pinCancelButton")
                        .disabled(viewModel.access.operation == .savingPin)
                }
            }
        }
        .onAppear(perform: viewModel.refreshBiometricAvailability)
        .onDisappear(perform: viewModel.cancelPinChange)
    }

    private var biometricTitle: String {
        switch viewModel.access.biometricKind {
        case .faceID: "Unlock with Face ID"
        case .touchID: "Unlock with Touch ID"
        case .generic: "Biometric unlock"
        }
    }
}
