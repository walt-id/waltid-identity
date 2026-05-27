import SwiftUI

struct ContentView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        HomeView(viewModel: viewModel)
    }
}

#Preview {
    ContentView(viewModel: WalletViewModel())
}
