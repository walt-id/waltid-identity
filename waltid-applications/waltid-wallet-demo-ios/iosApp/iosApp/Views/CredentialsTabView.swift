import SwiftUI
import WalletSDK

struct CredentialsTabView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var path: [String]

    private var details: [CredentialDetails] {
        viewModel.credentials.map(CredentialDisplayNormalizer.details(for:))
    }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    StatusBannerView(
                        message: viewModel.statusMessage(for: .credentials),
                        isLoading: viewModel.statusIsLoading(for: .credentials),
                        isError: viewModel.statusIsError(for: .credentials)
                    )

                    if details.isEmpty {
                        EmptyCredentialsView()
                    } else {
                        ForEach(details) { item in
                            CredentialCardButton(details: item) {
                                path.append(item.id)
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Credentials")
            .navigationDestination(for: String.self) { credentialID in
                CredentialDetailsDestination(
                    credentialID: credentialID,
                    details: details
                )
            }
        }
    }
}
