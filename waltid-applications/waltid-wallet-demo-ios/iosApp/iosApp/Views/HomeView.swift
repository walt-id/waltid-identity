import SwiftUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                StatusBannerView(
                    message: viewModel.statusMessage,
                    isLoading: viewModel.isLoading,
                    isError: viewModel.isError
                )

                ReceiveView(viewModel: viewModel)
                Divider()
                PresentView(viewModel: viewModel)
                Divider()

                Text("Credentials")
                    .font(.headline)

                if viewModel.credentials.isEmpty {
                    Text("No credentials")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(viewModel.credentials, id: \.id) { credential in
                        CredentialCardView(credential: credential)
                    }
                }
            }
            .padding()
        }
    }
}
