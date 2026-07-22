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
    let onCancel: () -> Void
    var showsActions = true

    private var optionsByQuery: [(queryID: String, options: [PresentationCredentialOption])] {
        var queryIDs: [String] = []
        var groups: [String: [PresentationCredentialOption]] = [:]
        for option in preview.credentialOptions {
            if groups[option.queryID] == nil {
                queryIDs.append(option.queryID)
            }
            groups[option.queryID, default: []].append(option)
        }
        return queryIDs.map { ($0, groups[$0] ?? []) }
    }

    private var queryLabels: [String: String] {
        Dictionary(uniqueKeysWithValues: optionsByQuery.enumerated().map { index, group in
            (group.queryID, "Request \(index + 1)")
        })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VerifierReviewSections(request: preview.request)

            Text("Select credentials to share")
                .font(.subheadline.weight(.semibold))

            if !preview.credentialRequirements.isEmpty {
                ReviewMetadataSection(title: "Required credential combinations") {
                    ForEach(Array(preview.credentialRequirements.enumerated()), id: \.offset) { index, requirement in
                        if index > 0 { Divider() }
                        Text("Requirement \(index + 1)")
                            .font(.caption.weight(.medium))
                        Text(
                            requirement.options
                                .map { option in
                                    option.map { queryLabels[$0] ?? $0 }.joined(separator: " + ")
                                }
                                .joined(separator: "  or  ")
                        )
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            ForEach(Array(optionsByQuery.enumerated()), id: \.element.queryID) { index, group in
                PresentationQueryGroupView(
                    title: "Request \(index + 1)",
                    queryID: group.queryID,
                    options: group.options,
                    selectedCredentialOptions: selectedCredentialOptions,
                    selectedDisclosureOptions: selectedDisclosureOptions,
                    isLoading: isLoading,
                    isReadOnly: isReadOnly,
                    onToggleCredential: onToggleCredential,
                    onToggleDisclosure: onToggleDisclosure,
                    onCredentialSelected: onCredentialSelected
                )
                Divider()
            }

            if !isReadOnly && showsActions {
                PresentationReviewActionsView(
                    selectionComplete: selectionComplete,
                    isLoading: isLoading,
                    onSubmit: onSubmit,
                    onReject: onReject,
                    onCancel: onCancel
                )
            }
        }
    }
}

private struct PresentationQueryGroupView: View {
    let title: String
    let queryID: String
    let options: [PresentationCredentialOption]
    let selectedCredentialOptions: Set<PresentationCredentialSelection>
    let selectedDisclosureOptions: Set<PresentationDisclosureSelection>
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    let onCredentialSelected: (String) -> Void
    @State private var page = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.subheadline.weight(.semibold))

            if !options.isEmpty {
                PresentationCredentialOptionView(
                    option: options[page],
                    selectedCredentialOptions: selectedCredentialOptions,
                    selectedDisclosureOptions: selectedDisclosureOptions,
                    isLoading: isLoading,
                    isReadOnly: isReadOnly,
                    onToggleCredential: onToggleCredential,
                    onToggleDisclosure: onToggleDisclosure,
                    onCredentialSelected: onCredentialSelected
                )
                CarouselControls(page: $page, pageCount: options.count, itemName: "credential")
            }
        }
    }
}

private struct PresentationCredentialOptionView: View {
    let option: PresentationCredentialOption
    let selectedCredentialOptions: Set<PresentationCredentialSelection>
    let selectedDisclosureOptions: Set<PresentationDisclosureSelection>
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    let onCredentialSelected: (String) -> Void

    var body: some View {
        let details = CredentialDisplayNormalizer.details(for: option)
        let requestedDisclosureItems = details.groups
            .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
            .items ?? []
        VStack(alignment: .leading, spacing: 10) {
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

struct PresentationReviewActionsView: View {
    let selectionComplete: Bool
    let isLoading: Bool
    let onSubmit: () -> Void
    let onReject: () -> Void
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button("Share", action: onSubmit)
                .buttonStyle(.borderedProminent)
                .tint(.waltBlue)
                .disabled(isLoading || !selectionComplete)
                .accessibilityIdentifier(WalletAccessibilityID.presentationSubmitButton)

            Button("Cancel review", action: onCancel)
                .buttonStyle(.bordered)
                .disabled(isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.presentationCancelButton)

            Button("Reject", action: onReject)
                .buttonStyle(.bordered)
                .disabled(isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.presentationRejectButton)
        }
    }
}
