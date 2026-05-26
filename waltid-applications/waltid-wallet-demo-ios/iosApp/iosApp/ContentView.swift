import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = WalletViewModel()

    var body: some View {
        NavigationStack {
            HomeView(viewModel: viewModel)
                .navigationTitle("walt.id Wallet")
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        NavigationLink(destination: SettingsView(viewModel: viewModel)) {
                            Image(systemName: "gear")
                        }
                    }
                }
        }
        .tint(.waltBlue)
    }
}

#Preview {
    ContentView()
}
