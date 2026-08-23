import SwiftUI
import WalletDemoSharingUI

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?
    var showsInput = true
    var onClose: (() -> Void)? = nil
    @Environment(\.openURL) private var openURL
    @State private var reviewRoute: ReviewRoute = .summary

    private var receivedDetails: [CredentialDetails] {
        viewModel.receivedCredentials.map(CredentialDisplayNormalizer.details(for:))
    }

    private var screenTitle: String {
        if case .technicalDetails(let islandID) = reviewRoute,
           let island = viewModel.offerPreview?.reviewIslands.first(where: { $0.id == islandID }) {
            return island.title
        }
        return viewModel.offerPreview == nil ? "Receive" : "Add credential"
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Color.clear
                        .frame(width: 1, height: 1)
                        .accessibilityElement()
                        .accessibilityIdentifier(WalletAccessibilityID.receiveTabContent)

                    if viewModel.offerPreview == nil && showsInput {
                        ScannableUrlEditor(
                            title: nil,
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
                        .buttonStyle(WalletPrimaryButtonStyle())
                        .disabled(!viewModel.receiveActionEnabled)
                        .accessibilityIdentifier(WalletAccessibilityID.receiveButton)
                    }

                    if viewModel.statusShouldPersist(for: .receive) {
                        StatusBannerView(
                            message: viewModel.statusMessage(for: .receive),
                            isLoading: viewModel.statusIsLoading(for: .receive),
                            isError: viewModel.statusIsError(for: .receive),
                            onDismiss: viewModel.dismissStatus
                        )
                    }

                    if let preview = viewModel.offerPreview {
                        OfferReviewView(
                            preview: preview,
                            isAcceptEnabled: viewModel.acceptOfferEnabled,
                            isReviewEnabled: viewModel.offerReviewEnabled,
                            txCode: viewModel.txCode,
                            showsActions: false,
                            route: $reviewRoute,
                            showsTechnicalHeader: false,
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
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(screenTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if reviewRoute != .summary {
                        Button { reviewRoute = .summary } label: {
                            Image(systemName: "chevron.left")
                        }
                        .accessibilityLabel("Back to review")
                    } else {
                        Button { onClose?() } label: {
                            Image(systemName: "xmark")
                        }
                        .accessibilityLabel("Close")
                        .disabled(onClose == nil)
                        .opacity(onClose == nil ? 0 : 1)
                    }
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
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
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
        .onChange(of: viewModel.offerPreview == nil) { _ in reviewRoute = .summary }
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
