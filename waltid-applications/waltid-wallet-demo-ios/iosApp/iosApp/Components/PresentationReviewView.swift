import SwiftUI
import WalletSDK

struct PresentationReviewView: View {
    let preview: PresentationPreview
    let selectedCredentialIDs: Set<String>
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (String) -> Void
    let onCredentialSelected: (String) -> Void
    let onSubmit: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VerifierDetailsView(request: preview.request)

            Text("Shared credentials")
                .font(.subheadline.weight(.semibold))

            ForEach(preview.credentialOptions) { option in
                VStack(alignment: .leading, spacing: 10) {
                    let details = CredentialDisplayNormalizer.details(for: option)
                    if !isReadOnly {
                        Toggle(isOn: Binding(get: {
                            selectedCredentialIDs.contains(option.credentialID)
                        }, set: { _ in
                            onToggleCredential(option.credentialID)
                        })) {
                            Text(option.label ?? option.format)
                                .font(.subheadline.weight(.medium))
                        }
                        .disabled(isLoading)
                        .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.credentialID))
                    }

                    CredentialCardButton(details: details) {
                        onCredentialSelected(option.credentialID)
                    }
                    .padding(.leading, isReadOnly ? 0 : 28)

                    Divider()
                }
            }

            if !isReadOnly {
                PresentationReviewActionsView(
                    selectedCredentialIDs: selectedCredentialIDs,
                    isLoading: isLoading,
                    onSubmit: onSubmit,
                    onCancel: onCancel
                )
            }
        }
    }
}

private struct PresentationReviewActionsView: View {
    let selectedCredentialIDs: Set<String>
    let isLoading: Bool
    let onSubmit: () -> Void
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button("Share", action: onSubmit)
                .buttonStyle(.borderedProminent)
                .tint(.waltBlue)
                .disabled(isLoading || selectedCredentialIDs.isEmpty)
                .accessibilityIdentifier(WalletAccessibilityID.presentationSubmitButton)

            Button("Cancel review", action: onCancel)
                .buttonStyle(.bordered)
                .disabled(isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.presentationCancelButton)
        }
    }
}
