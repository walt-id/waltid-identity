import SwiftUI
import WalletSDK

struct PresentationReviewView: View {
    let preview: PresentationPreview
    let selectedCredentialOptions: Set<PresentationCredentialSelection>
    let selectedDisclosureOptions: Set<PresentationDisclosureSelection>
    let selectionComplete: Bool
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    let onCredentialSelected: (String) -> Void
    let onSubmit: () -> Void
    let onReject: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VerifierReviewSections(request: preview.request)

            Text("Select credentials to share")
                .font(.subheadline.weight(.semibold))

            ForEach(preview.credentialOptions) { option in
                VStack(alignment: .leading, spacing: 10) {
                    let details = CredentialDisplayNormalizer.details(for: option)
                    let requestedDisclosureItems = details.groups
                        .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
                        .items ?? []
                    if !isReadOnly {
                        Toggle(isOn: Binding(get: {
                            selectedCredentialOptions.contains(option.selection)
                        }, set: { _ in
                            onToggleCredential(option.selection)
                        })) {
                            Text(option.label ?? option.format)
                                .font(.subheadline.weight(.medium))
                        }
                        .disabled(isLoading)
                        .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.selection.id))
                    }

                    CredentialCardButton(details: details) {
                        onCredentialSelected(details.id)
                    }
                    .padding(.leading, isReadOnly ? 0 : 28)

                    if !option.disclosures.isEmpty {
                        PresentationDisclosureListView(
                            option: option,
                            selectedCredentialOptions: selectedCredentialOptions,
                            selectedDisclosureOptions: selectedDisclosureOptions,
                            requestedDisclosureItems: requestedDisclosureItems,
                            isLoading: isLoading,
                            isReadOnly: isReadOnly,
                            onToggleDisclosure: onToggleDisclosure
                        )
                        .padding(.leading, isReadOnly ? 0 : 28)
                    }

                    Divider()
                }
            }

            if !isReadOnly {
                PresentationReviewActionsView(
                    selectionComplete: selectionComplete,
                    isLoading: isLoading,
                    onSubmit: onSubmit,
                    onReject: onReject
                )
            }
        }
    }
}

private struct PresentationDisclosureListView: View {
    let option: PresentationCredentialOption
    let selectedCredentialOptions: Set<PresentationCredentialSelection>
    let selectedDisclosureOptions: Set<PresentationDisclosureSelection>
    let requestedDisclosureItems: [ClaimItem]
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void

    private var credentialSelected: Bool {
        selectedCredentialOptions.contains(option.selection)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(CredentialDisplayVocabulary.requestedDisclosuresTitle)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)

            ForEach(Array(option.disclosures.enumerated()), id: \.element.id) { index, disclosure in
                let selection = PresentationDisclosureSelection(
                    queryID: option.queryID,
                    credentialID: option.credentialID,
                    path: disclosure.path
                )

                VStack(alignment: .leading, spacing: 4) {
                    if disclosure.selectable && !isReadOnly {
                        Toggle(isOn: Binding(get: {
                            selectedDisclosureOptions.contains(selection)
                        }, set: { _ in
                            onToggleDisclosure(selection)
                        })) {
                            if requestedDisclosureItems.indices.contains(index) {
                                ClaimValueRow(item: requestedDisclosureItems[index])
                            } else {
                                DisclosureTextView(disclosure: disclosure)
                            }
                        }
                        .disabled(isLoading || !credentialSelected)
                        .accessibilityIdentifier(WalletAccessibilityID.presentationDisclosureToggle(selection.id))
                    } else if requestedDisclosureItems.indices.contains(index) {
                        ClaimValueRow(item: requestedDisclosureItems[index])
                    } else {
                        DisclosureTextView(disclosure: disclosure)
                    }

                    Text(disclosure.presentationDisclosureStatusText)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .accessibilityIdentifier(WalletAccessibilityID.presentationDisclosure(selection.id))
            }
        }
    }
}

private extension PresentationDisclosure {
    var presentationDisclosureStatusText: String {
        if selectable { return "Optional disclosure" }
        if required { return "Required by request" }
        if selectivelyDisclosable { return "Selective disclosure" }
        return "Required by credential format"
    }
}

private struct DisclosureTextView: View {
    let disclosure: PresentationDisclosure

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(disclosure.name ?? disclosure.path)
                .font(.caption.weight(.medium))
                .foregroundStyle(.primary)
            Text(disclosure.displayValue ?? disclosure.valueJSON)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(4)
        }
    }
}

private struct PresentationReviewActionsView: View {
    let selectionComplete: Bool
    let isLoading: Bool
    let onSubmit: () -> Void
    let onReject: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button("Share", action: onSubmit)
                .buttonStyle(.borderedProminent)
                .tint(.waltBlue)
                .disabled(isLoading || !selectionComplete)
                .accessibilityIdentifier(WalletAccessibilityID.presentationSubmitButton)

            Button("Decline", action: onReject)
                .buttonStyle(.bordered)
                .disabled(isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.presentationRejectButton)
        }
    }
}
