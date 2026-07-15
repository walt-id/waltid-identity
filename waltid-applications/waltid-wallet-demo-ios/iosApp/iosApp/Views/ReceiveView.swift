import SwiftUI

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?

    private var receivedDetails: [CredentialDetails] {
        viewModel.receivedCredentials.map(CredentialDisplayNormalizer.details(for:))
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    UrlEditor(
                        title: "Receive",
                        label: "Credential offer URL",
                        text: $viewModel.offerUrl,
                        inputIdentifier: WalletAccessibilityID.offerInput,
                        isEnabled: viewModel.receiveUrlEntryEnabled,
                        focusResetKey: viewModel.inputFocusResetKey
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

                    if let warning = viewModel.transactionDataProfilesWarning {
                        WarningBannerView(message: warning)
                    }

                    if viewModel.receiveCompleted {
                        Button("New receive", action: viewModel.startNewReceiveFlow)
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier(WalletAccessibilityID.receiveNewButton)

                        Text("Received credentials")
                            .font(.subheadline.weight(.semibold))

                        ForEach(receivedDetails) { item in
                            CredentialCardButton(details: item) {
                                selectedDetailsID = item.id
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Receive")
            .background(detailsNavigationLink)
            .accessibilityIdentifier(WalletAccessibilityID.receiveTabContent)
        }
        .navigationViewStyle(.stack)
    }

    private var detailsNavigationLink: some View {
        NavigationLink(
            destination: detailsDestination,
            isActive: Binding(
                get: { selectedDetailsID != nil },
                set: { isActive in
                    if !isActive {
                        selectedDetailsID = nil
                    }
                }
            )
        ) {
            EmptyView()
        }
        .hidden()
    }

    private var detailsDestination: some View {
        Group {
            if let detailsID = selectedDetailsID {
                CredentialDetailsDestination(
                    detailsID: detailsID,
                    details: receivedDetails
                )
            } else {
                EmptyView()
            }
        }
    }
}
