import SwiftUI
import shared

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        VStack(spacing: 16) {
            StatusBannerView(
                message: viewModel.statusMessage,
                isLoading: viewModel.isLoading,
                isError: viewModel.isError
            )

            if !viewModel.isBootstrapped {
                Spacer()
                VStack(spacing: 12) {
                    Text("No wallet initialized")
                        .font(.title2)
                        .bold()
                    Text("Generate a key and DID to get started.")
                        .foregroundColor(.secondary)
                    Button("Initialize Wallet") {
                        viewModel.bootstrap()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.waltBlue)
                }
                Spacer()
            } else {
                Text(viewModel.did)
                    .font(.caption2)
                    .foregroundColor(.gray)
                    .lineLimit(1)
                    .truncationMode(.middle)

                HStack(spacing: 12) {
                    NavigationLink(destination: ReceiveView(viewModel: viewModel)) {
                        Label("Receive", systemImage: "plus.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(.waltBlue)

                    NavigationLink(destination: PresentView(viewModel: viewModel)) {
                        Label("Present", systemImage: "arrow.up.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(.waltBlue)
                }

                Section {
                    Text("Credentials (\(viewModel.credentials.count))")
                        .font(.headline)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if viewModel.credentials.isEmpty {
                        Text("No credentials yet. Tap Receive to accept a credential offer.")
                            .foregroundColor(.secondary)
                            .font(.subheadline)
                    } else {
                        ForEach(viewModel.credentials, id: \.id) { credential in
                            CredentialCardView(credential: credential)
                        }
                    }
                }
            }
        }
        .padding()
    }
}
