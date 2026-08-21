import SwiftUI
import WalletDemoSharingUI

struct ContentView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        switch viewModel.auth {
        case .setup, .login:
            PinView(viewModel: viewModel)
        case .storageUnavailable(let message):
            pinStorageUnavailable(message)
        case .unlocked:
            HomeView(viewModel: viewModel)
        }
    }

    private func pinStorageUnavailable(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("walt.id Wallet")
                .font(.largeTitle.weight(.bold))
            Text("PIN storage unavailable")
                .font(.title3.weight(.semibold))
            Text("\(message). The wallet remains locked.")
                .foregroundColor(.red)
            Spacer()
        }
        .padding(24)
    }
}

#Preview {
    ContentView(viewModel: WalletViewModel.mockForUITests())
}
