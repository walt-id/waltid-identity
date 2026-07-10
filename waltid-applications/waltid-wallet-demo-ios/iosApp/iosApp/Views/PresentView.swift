import SwiftUI

struct PresentView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var path: [String]

    private var presentationDetails: [CredentialDetails] {
        viewModel.presentationPreview?.credentialOptions.map(CredentialDisplayNormalizer.details(for:)) ?? []
    }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    UrlEditor(
                        title: "Present",
                        label: "OpenID4VP request URL",
                        text: $viewModel.presentationRequestUrl,
                        inputIdentifier: WalletAccessibilityID.presentationInput,
                        isEnabled: viewModel.presentationUrlEntryEnabled
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
                            selectedCredentialIDs: viewModel.selectedPresentationCredentialIDs,
                            isLoading: !viewModel.presentationReviewEnabled,
                            isReadOnly: viewModel.presentationCompleted,
                            onToggleCredential: viewModel.togglePresentationCredential,
                            onCredentialSelected: { credentialID in path.append(credentialID) },
                            onSubmit: viewModel.submitPresentation,
                            onCancel: viewModel.cancelPresentationReview
                        )
                    }
                }
                .padding()
            }
            .navigationTitle("Present")
            .navigationDestination(for: String.self) { credentialID in
                CredentialDetailsDestination(
                    credentialID: credentialID,
                    details: presentationDetails
                )
            }
            .accessibilityIdentifier(WalletAccessibilityID.presentTabContent)
        }
    }
}
