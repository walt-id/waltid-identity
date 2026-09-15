import SwiftUI
import WalletDemoSharingUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var selectedCredentialDetailsID: String?

    var body: some View {
        Group {
            if !viewModel.isReady, let model = viewModel.identityScreen {
                NavigationView {
                    WalletSetupView(viewModel: viewModel, model: model)
                }.navigationViewStyle(.stack)
            } else { walletTabs }
        }
    }

    private var walletTabs: some View {
        TabView(selection: $viewModel.selectedTab) {
            CredentialsTabView(
                viewModel: viewModel,
                selectedDetailsID: $selectedCredentialDetailsID
            )
            .tabItem {
                Label("Credentials", systemImage: "wallet.pass")
            }
            .tag(WalletTab.credentials)

            ReceiveView(
                viewModel: viewModel
            )
            .tabItem {
                Label("Receive", systemImage: "tray.and.arrow.down")
            }
            .tag(WalletTab.receive)

            PresentView(
                viewModel: viewModel
            )
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
