import SwiftUI
import WalletDemoSharingUI

struct ContentView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        switch viewModel.auth {
        case .setup, .login:
            PinView(viewModel: viewModel)
        case .storageUnavailable(let message):
            VStack(alignment: .leading, spacing: 16) {
                Text("walt.id Wallet")
                    .font(.largeTitle.bold())
                Text("PIN storage unavailable")
                    .font(.title3.weight(.semibold))
                Text("\(message). The wallet remains locked.")
                    .foregroundStyle(.red)
                Spacer()
            }
            .padding(24)
        case .unlocked:
            HomeView(viewModel: viewModel)
        }
    }
}

#Preview {
    ContentView(viewModel: WalletViewModel.mockForUITests())
}