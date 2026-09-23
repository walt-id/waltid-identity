import SwiftUI
import WalletDemoSharingUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var selectedCredentialDetailsID: String?
    @State private var showingSettings = false

    var body: some View {
        Group {
            if !viewModel.isReady, let model = viewModel.identityScreen {
                NavigationView {
                    WalletSetupView(viewModel: viewModel, model: model)
                }.navigationViewStyle(.stack)
            } else { walletTabs }
        }
        .fullScreenCover(isPresented: $showingSettings) {
            NavigationView {
                SettingsView(viewModel: viewModel)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button { showingSettings = false } label: {
                                Label("Back", systemImage: "chevron.backward")
                            }
                            .accessibilityIdentifier("wallet.settingsBack")
                        }
                    }
            }.navigationViewStyle(.stack)
        }
        .onChange(of: viewModel.isReady) { ready in if !ready { showingSettings = false } }
    }

    private func openSettings() {
        viewModel.proximityPresentation.dismiss()
        showingSettings = true
    }

    private var walletTabs: some View {
        TabView(selection: $viewModel.selectedTab) {
            CredentialsTabView(
                viewModel: viewModel,
                selectedDetailsID: $selectedCredentialDetailsID,
                onOpenSettings: openSettings
            )
            .tabItem {
                Label("Credentials", systemImage: "wallet.pass")
            }
            .tag(WalletTab.credentials)

            ReceiveView(viewModel: viewModel, onOpenSettings: openSettings)
                .tabItem {
                    Label("Receive", systemImage: "tray.and.arrow.down")
                }
                .tag(WalletTab.receive)

            PresentView(viewModel: viewModel, onOpenSettings: openSettings)
                .tabItem {
                    Label("Present", systemImage: "person.badge.key")
                }
                .tag(WalletTab.present)
        }
    }
}

/// Keep wallet-opening status visible after the key operation succeeds.
private struct WalletSetupView: View {
    @ObservedObject var viewModel: WalletViewModel
    @ObservedObject var model: WalletIdentityScreenModel

    var body: some View {
        if model.identity == nil {
            WalletIdentityView(model: model)
        } else {
            VStack(alignment: .leading, spacing: 16) {
                Text("Your signing key is ready").font(.title2)
                if viewModel.isLoading {
                    ProgressView("Opening wallet…")
                } else {
                    Text(viewModel.statusMessage).foregroundStyle(.secondary)
                    Button("Retry opening wallet") { viewModel.retryOpeningWallet() }
                        .buttonStyle(.borderedProminent)
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .navigationTitle("Set up your wallet")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
