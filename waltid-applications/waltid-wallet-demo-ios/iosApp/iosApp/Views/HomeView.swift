import SwiftUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var credentialsPath: [String] = []
    @State private var receivePath: [String] = []
    @State private var presentPath: [String] = []

    var body: some View {
        TabView(selection: $viewModel.selectedTab) {
            CredentialsTabView(
                viewModel: viewModel,
                path: $credentialsPath
            )
            .tabItem {
                Label("Credentials", systemImage: "wallet.pass")
            }
            .tag(WalletTab.credentials)

            ReceiveView(
                viewModel: viewModel,
                path: $receivePath
            )
            .tabItem {
                Label("Receive", systemImage: "tray.and.arrow.down")
            }
            .tag(WalletTab.receive)

            PresentView(
                viewModel: viewModel,
                path: $presentPath
            )
            .tabItem {
                Label("Present", systemImage: "person.badge.key")
            }
            .tag(WalletTab.present)
        }
        .onChange(of: viewModel.receiveNavigationResetKey) { _ in
            receivePath = []
        }
        .onChange(of: viewModel.presentationNavigationResetKey) { _ in
            presentPath = []
        }
    }
}
