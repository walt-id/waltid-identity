import SwiftUI

struct PresentView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?

    private var presentationDetails: [CredentialDetails] {
        viewModel.presentationPreview?.credentialOptions.map(CredentialDisplayNormalizer.details(for:)) ?? []
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    UrlEditor(
                        title: "Present",
                        label: "OpenID4VP request URL",
                        text: $viewModel.presentationRequestUrl,
                        inputIdentifier: WalletAccessibilityID.presentationInput,
                        isEnabled: viewModel.presentationUrlEntryEnabled,
                        focusResetKey: viewModel.inputFocusResetKey
                    )

                    Button("Preview") {
                        viewModel.previewPresentation()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.waltBlue)
                    .disabled(!viewModel.presentationPreviewActionEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.presentButton)

                    if viewModel.credentials.isEmpty {
                        Text("No credentials available")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    StatusBannerView(
                        message: viewModel.statusMessage(for: .present),
                        isLoading: viewModel.statusIsLoading(for: .present),
                        isError: viewModel.statusIsError(for: .present)
                    )

                    if viewModel.presentationCompleted {
                        Button("New presentation", action: viewModel.startNewPresentationFlow)
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier(WalletAccessibilityID.presentationNewButton)
                    }

                    if let preview = viewModel.presentationPreview {
                        PresentationReviewView(
                            preview: preview,
                            selectedCredentialOptions: viewModel.selectedPresentationCredentialOptions,
                            selectedDisclosureOptions: viewModel.selectedPresentationDisclosureOptions,
                            selectionComplete: viewModel.presentationCredentialSelectionComplete,
                            isLoading: !viewModel.presentationReviewEnabled,
                            isReadOnly: viewModel.presentationCompleted,
                            onToggleCredential: viewModel.togglePresentationCredential,
                            onToggleDisclosure: viewModel.togglePresentationDisclosure,
                            onCredentialSelected: { detailsID in selectedDetailsID = detailsID },
                            onSubmit: viewModel.submitPresentation,
                            onCancel: viewModel.cancelPresentationReview
                        )
                    }
                }
                .padding()
            }
            .navigationTitle("Present")
            .background(detailsNavigationLink)
            .accessibilityIdentifier(WalletAccessibilityID.presentTabContent)
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
                    details: presentationDetails
                )
            } else {
                EmptyView()
            }
        }
    }
}
