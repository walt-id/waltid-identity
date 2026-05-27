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

                if !viewModel.did.isEmpty {
                    Text("DID: \(viewModel.did)")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }

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
