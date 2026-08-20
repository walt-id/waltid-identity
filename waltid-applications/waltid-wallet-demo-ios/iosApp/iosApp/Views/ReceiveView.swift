import SwiftUI
import WalletDemoSharingUI

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?
    var showsInput = true
    var onClose: (() -> Void)? = nil
    @Environment(\.openURL) private var openURL

    private var receivedDetails: [CredentialDetails] {
        viewModel.receivedCredentials.map(CredentialDisplayNormalizer.details(for:))
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Color.clear
                        .frame(width: 1, height: 1)
                        .accessibilityElement()
                        .accessibilityIdentifier(WalletAccessibilityID.receiveTabContent)

                    if viewModel.offerPreview == nil && showsInput {
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
                    }

                    StatusBannerView(
                        message: viewModel.statusMessage(for: .receive),
                        isLoading: viewModel.statusIsLoading(for: .receive),
                        isError: viewModel.statusIsError(for: .receive)
                    )

                    if let preview = viewModel.offerPreview {
                        OfferReviewView(
                            preview: preview,
                            isAcceptEnabled: viewModel.acceptOfferEnabled,
                            isReviewEnabled: viewModel.offerReviewEnabled,
                            txCode: viewModel.txCode,
                            showsActions: false,
                            onTxCodeChange: viewModel.updateTxCode,
                            onAccept: viewModel.acceptOffer,
                            onDecline: viewModel.declineOffer
                        )
                    }

                    if let warning = viewModel.transactionDataProfilesWarning {
                        WarningBannerView(message: warning)
                    }

                    if !viewModel.deferredCredentials.isEmpty {
                        Text("Pending credentials")
                            .font(.subheadline.weight(.semibold))
                        ForEach(viewModel.deferredCredentials, id: \.id) { credential in
                            Button("Check \(credential.credentialConfigurationID)") {
                                viewModel.resumeDeferredCredential(credential)
                            }
                            .buttonStyle(.bordered)
                            .disabled(viewModel.isLoading)
                        }
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
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { onClose?() }
                        .disabled(onClose == nil)
                        .opacity(onClose == nil ? 0 : 1)
                }
            }
            .safeAreaInset(edge: .bottom) {
                if let preview = viewModel.offerPreview, !viewModel.receiveCompleted {
                    OfferReviewActions(
                        preview: preview,
                        isAcceptEnabled: viewModel.acceptOfferEnabled,
                        isReviewEnabled: viewModel.offerReviewEnabled,
                        onAccept: viewModel.acceptOffer,
                        onDecline: viewModel.declineOffer
                    )
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(.regularMaterial)
                }
            }
            .background(detailsNavigationLink)
        }
        .navigationViewStyle(.stack)
        .onChange(of: viewModel.authorizationRequestURL) { authorizationURL in
            guard let authorizationURL else { return }
            openURL(authorizationURL)
            viewModel.authorizationRequestOpened()
        }
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
