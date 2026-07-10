import SwiftUI

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var path: [String]

    private var receivedDetails: [CredentialDetails] {
        viewModel.receivedCredentials.map(CredentialDisplayNormalizer.details(for:))
    }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    UrlEditor(
                        title: "Receive",
                        label: "Credential offer URL",
                        text: $viewModel.offerUrl,
                        inputIdentifier: WalletAccessibilityID.offerInput,
                        isEnabled: viewModel.receiveUrlEntryEnabled
                    )

                    Button("Receive") {
                        viewModel.receiveCredential()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.waltBlue)
                    .disabled(!viewModel.receiveActionEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.receiveButton)

                    StatusBannerView(
                        message: viewModel.statusMessage(for: .receive),
                        isLoading: viewModel.statusIsLoading(for: .receive),
                        isError: viewModel.statusIsError(for: .receive)
                    )

                    if viewModel.receiveCompleted {
                        Button("New receive", action: viewModel.startNewReceiveFlow)
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier(WalletAccessibilityID.receiveNewButton)

                        Text("Received credentials")
                            .font(.subheadline.weight(.semibold))

                        ForEach(receivedDetails) { item in
                            CredentialCardButton(details: item) {
                                path.append(item.id)
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Receive")
            .navigationDestination(for: String.self) { credentialID in
                CredentialDetailsDestination(
                    credentialID: credentialID,
                    details: receivedDetails
                )
            }
            .accessibilityIdentifier(WalletAccessibilityID.receiveTabContent)
        }
    }
}
