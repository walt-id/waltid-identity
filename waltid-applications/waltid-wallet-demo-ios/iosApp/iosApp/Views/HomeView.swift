import SwiftUI
import WalletDemoSharingUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var selectedCredentialDetailsID: String?

    var body: some View {
        walletTabs
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
