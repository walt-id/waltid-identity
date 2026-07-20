import SwiftUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var selectedCredentialDetailsID: String?
    @State private var selectedReceiveDetailsID: String?
    @State private var selectedPresentationDetailsID: String?

    var body: some View {
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
                viewModel: viewModel,
                selectedDetailsID: $selectedReceiveDetailsID
            )
            .tabItem {
                Label("Receive", systemImage: "tray.and.arrow.down")
            }
            .tag(WalletTab.receive)

            PresentView(
                viewModel: viewModel,
                selectedDetailsID: $selectedPresentationDetailsID
            )
            .tabItem {
                Label("Present", systemImage: "person.badge.key")
            }
            .tag(WalletTab.present)
        }
        .onChange(of: viewModel.receiveNavigationResetKey) { _ in
            selectedReceiveDetailsID = nil
        }
        .onChange(of: viewModel.presentationNavigationResetKey) { _ in
            selectedPresentationDetailsID = nil
        }
    }
}
