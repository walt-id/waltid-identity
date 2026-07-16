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
                    if viewModel.offerPreview == nil {
                        ScannableUrlEditor(
                            title: "Receive",
                            label: "Credential offer URL",
                            text: $viewModel.offerUrl,
                            inputIdentifier: WalletAccessibilityID.offerInput,
                            scanButtonIdentifier: WalletAccessibilityID.offerScanButton,
                            isEnabled: viewModel.receiveUrlEntryEnabled,
                            focusResetKey: viewModel.inputFocusResetKey
                        )

                        Button("Receive") {
                            viewModel.previewOffer()
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.waltBlue)
                        .disabled(!viewModel.receiveActionEnabled)
                        .accessibilityIdentifier(WalletAccessibilityID.receiveButton)
                    } else if let preview = viewModel.offerPreview {
                        OfferReviewView(
                            preview: preview,
                            isEnabled: viewModel.acceptOfferEnabled,
                            isTxCodeEnabled: viewModel.offerReviewEnabled,
                            txCode: viewModel.txCode,
                            onTxCodeChange: viewModel.updateTxCode,
                            onAccept: viewModel.acceptOffer,
                            onDecline: viewModel.declineOffer
                        )
                    }

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

                        MetadataIdentityCardView(
                            identity: CredentialDisplayNormalizer.metadataIdentity(
                                title: "Issuer",
                                rawJSON: viewModel.lastIssuerMetadataJSON,
                                fallbackName: viewModel.receivedCredentials.first?.issuer,
                                fallbackSubtitle: "Credential issuer"
                            )
                        )

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
