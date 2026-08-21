import SwiftUI
import WalletSDK

/// The wallet's single presentation-review surface, shared by every transport that can ask for a
/// credential.
///
/// The host owns the transport, the preview handle and the operating-system result; this view owns
/// only the rendering of what the user is being asked and the reporting of what they chose.
public struct SharingReviewView: View {
    private let review: SharingReviewModel
    private let selection: SharingSelection
    private let selectionComplete: Bool
    private let isLoading: Bool
    private let isReadOnly: Bool
    private let onToggleCredential: (PresentationCredentialSelection) -> Void
    private let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    private let onCredentialSelected: ((String) -> Void)?
    private let onSubmit: () -> Void
    private let onReject: (() -> Void)?
    private let onCancel: () -> Void
    private let compact: Bool

    /// Renders one sharing review.
    ///
    /// - Parameters:
    ///   - review: What the user is being asked to share, already mapped off the transport's preview.
    ///   - selection: Credentials and disclosures currently chosen.
    ///   - selectionComplete: Whether the request is satisfied, which is what enables Share.
    ///   - isLoading: Whether an operation is in flight, which disables every action.
    ///   - isReadOnly: Whether the review is a record of a finished presentation rather than a prompt.
    ///   - onCredentialSelected: Opens a credential's full details, when the host has somewhere to
    ///     open them.
    ///   - onReject: Sends a protocol-level refusal to the requester. Pass `nil` for transports with
    ///     no such message - the platform Digital Credentials APIs return a cancellation instead, and
    ///     offering both Reject and Cancel there would promise the requester gets told two different
    ///     things.
    public init(
        review: SharingReviewModel,
        selection: SharingSelection,
        selectionComplete: Bool,
        isLoading: Bool = false,
        isReadOnly: Bool = false,
        onToggleCredential: @escaping (PresentationCredentialSelection) -> Void,
        onToggleDisclosure: @escaping (PresentationDisclosureSelection) -> Void,
        onCredentialSelected: ((String) -> Void)? = nil,
        onSubmit: @escaping () -> Void,
        onReject: (() -> Void)? = nil,
        onCancel: @escaping () -> Void,
        compact: Bool = false
    ) {
        self.review = review
        self.selection = selection
        self.selectionComplete = selectionComplete
        self.isLoading = isLoading
        self.isReadOnly = isReadOnly
        self.onToggleCredential = onToggleCredential
        self.onToggleDisclosure = onToggleDisclosure
        self.onCredentialSelected = onCredentialSelected
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
        self.compact = compact
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SharingRequestSections(request: review.request, compact: compact)

            if compact {
                CredentialCardStackView(
                    details: review.credentialOptions.map(CredentialDisplayNormalizer.details(for:))
                ) { id in
                    onCredentialSelected?(id)
                }
            } else {
                Text("Select credentials to share")
                    .font(.subheadline.weight(.semibold))

                ForEach(review.credentialOptions) { option in
                    CredentialReviewCard(
                        option: option,
                        selection: selection,
                        isLoading: isLoading,
                        isReadOnly: isReadOnly,
                        onToggleCredential: onToggleCredential,
                        onToggleDisclosure: onToggleDisclosure,
                        onCredentialSelected: onCredentialSelected
                    )
                }
            }

            if !isReadOnly {
                ReviewActions(
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

/// One offered credential: what it is, and what would be disclosed from it.
struct CredentialReviewCard: View {
    let option: PresentationCredentialOption
    let selection: SharingSelection
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    let onCredentialSelected: ((String) -> Void)?

    var body: some View {
        let details = CredentialDisplayNormalizer.details(for: option)
        let requestedDisclosureItems = details.groups
            .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
            .items ?? []

        VStack(alignment: .leading, spacing: 10) {
            if !isReadOnly {
                Toggle(isOn: Binding(get: {
                    selection.credentials.contains(option.selection)
                }, set: { _ in
                    onToggleCredential(option.selection)
                })) {
                    Text(option.label ?? option.format)
                        .font(.subheadline.weight(.medium))
                }
                .disabled(isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.selection.id))
            }

            if let onCredentialSelected {
                CredentialCardButton(details: details) {
                    onCredentialSelected(details.id)
                }
                .padding(.leading, isReadOnly ? 0 : 28)
            } else {
                CredentialCardView(details: details)
                    .padding(.leading, isReadOnly ? 0 : 28)
            }

            if !option.disclosures.isEmpty {
                DisclosureList(
                    option: option,
                    credentialSelected: selection.credentials.contains(option.selection),
                    selectedDisclosureOptions: selection.disclosures,
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
}

/// What this credential would actually reveal, claim by claim.
struct DisclosureList: View {
    let option: PresentationCredentialOption
    let credentialSelected: Bool
    let selectedDisclosureOptions: Set<PresentationDisclosureSelection>
    let requestedDisclosureItems: [ClaimItem]
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void

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
                            disclosureLabel(index: index, disclosure: disclosure)
                        }
                        .disabled(isLoading || !credentialSelected)
                        .accessibilityIdentifier(WalletAccessibilityID.presentationDisclosureToggle(selection.id))
                    } else {
                        disclosureLabel(index: index, disclosure: disclosure)
                    }

                    Text(disclosure.disclosureStatusText)
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

    @ViewBuilder
    private func disclosureLabel(index: Int, disclosure: PresentationDisclosure) -> some View {
        if requestedDisclosureItems.indices.contains(index) {
            ClaimValueRow(item: requestedDisclosureItems[index])
        } else {
            DisclosureTextView(disclosure: disclosure)
        }
    }
}

private extension PresentationDisclosure {
    /// Why this claim is in the request, in the user's terms rather than the format's.
    var disclosureStatusText: String {
        if selectable { return "Optional disclosure" }
        if required { return "Required by request" }
        if selectivelyDisclosable { return "Selective disclosure" }
        return "Required by credential format"
    }
}

/// Fallback rendering for a disclosure the display normalizer did not produce a claim row for.
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

/// Share, and the ways of declining the transport actually supports.
struct ReviewActions: View {
    @Environment(\.walletDemoBranding) private var branding
    let selectionComplete: Bool
    let isLoading: Bool
    let onSubmit: () -> Void
    let onReject: (() -> Void)?
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button("Share", action: onSubmit)
                .buttonStyle(.borderedProminent)
                .tint(branding.primary)
                .disabled(isLoading || !selectionComplete)
                .accessibilityIdentifier(WalletAccessibilityID.presentationSubmitButton)

            // Labelled "Cancel review" only where a protocol-level Reject also exists, so the two
            // ways of declining cannot be mistaken for each other.
            Button(onReject == nil ? "Cancel" : "Cancel review", action: onCancel)
                .buttonStyle(.bordered)
                .disabled(isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.presentationCancelButton)

            if let onReject {
                Button("Reject", action: onReject)
                    .buttonStyle(.bordered)
                    .disabled(isLoading)
                    .accessibilityIdentifier(WalletAccessibilityID.presentationRejectButton)
            }
        }
    }
}
