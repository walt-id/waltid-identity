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
    private let onSubmit: () -> Void
    private let onReject: (() -> Void)?
    private let onCancel: () -> Void
    private let compact: Bool
    private let showActions: Bool
    @State private var compactClaimsOption: PresentationCredentialOption?

    /// Renders one sharing review.
    ///
    /// - Parameters:
    ///   - review: What the user is being asked to share, already mapped off the transport's preview.
    ///   - selection: Credentials and disclosures currently chosen.
    ///   - selectionComplete: Whether the request is satisfied, which is what enables Share.
    ///   - isLoading: Whether an operation is in flight, which disables every action.
    ///   - isReadOnly: Whether the review is a record of a finished presentation rather than a prompt.
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
        onSubmit: @escaping () -> Void,
        onReject: (() -> Void)? = nil,
        onCancel: @escaping () -> Void,
        compact: Bool = false,
        showActions: Bool = true
    ) {
        self.review = review
        self.selection = selection
        self.selectionComplete = selectionComplete
        self.isLoading = isLoading
        self.isReadOnly = isReadOnly
        self.onToggleCredential = onToggleCredential
        self.onToggleDisclosure = onToggleDisclosure
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
        self.compact = compact
        self.showActions = showActions
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SharingRequestSections(request: review.request)

            if compact {
                CredentialCardStackView(
                    details: review.credentialOptions.map(CredentialDisplayNormalizer.details(for:))
                ) { id in
                    compactClaimsOption = review.credentialOptions.first {
                        CredentialDisplayNormalizer.details(for: $0).id == id
                    }
                }
                .sheet(isPresented: Binding(
                    get: { compactClaimsOption != nil },
                    set: { if !$0 { compactClaimsOption = nil } }
                )) {
                    if let option = compactClaimsOption {
                        let details = CredentialDisplayNormalizer.details(for: option)
                        SharingClaimsSheet(
                            option: option,
                            details: details,
                            credentialSelected: selection.credentials.contains(option.selection),
                            selectedDisclosureOptions: selection.disclosures,
                            requestedDisclosureItems: details.groups
                                .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
                                .items ?? [],
                            isLoading: isLoading,
                            isReadOnly: isReadOnly,
                            onToggleDisclosure: onToggleDisclosure,
                            onDismiss: { compactClaimsOption = nil }
                        )
                    }
                }
            } else {
                Text("Select credentials to share")
                    .font(.subheadline.weight(.semibold))

                if review.credentialOptions.isEmpty {
                    Text("No credentials available")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                ForEach(review.credentialOptions) { option in
                    CredentialReviewCard(
                        option: option,
                        selection: selection,
                        isLoading: isLoading,
                        isReadOnly: isReadOnly,
                        onToggleCredential: onToggleCredential,
                        onToggleDisclosure: onToggleDisclosure
                    )
                }
            }

            if !isReadOnly && showActions {
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

/// One offered credential: a selectable card that opens claim details.
struct CredentialReviewCard: View {
    let option: PresentationCredentialOption
    let selection: SharingSelection
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    @State private var claimsOpen = false

    var body: some View {
        let details = CredentialDisplayNormalizer.details(for: option)
        let requestedDisclosureItems = details.groups
            .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
            .items ?? []
        let credentialSelected = selection.credentials.contains(option.selection)

        HStack(alignment: .center, spacing: 12) {
            if !isReadOnly {
                Toggle(isOn: Binding(get: {
                    credentialSelected
                }, set: { _ in
                    onToggleCredential(option.selection)
                })) {
                    EmptyView()
                }
                .toggleStyle(ReviewCheckboxToggleStyle())
                .labelsHidden()
                .disabled(isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.presentationCredentialToggle(option.selection.id))
            }

            CredentialCardButton(details: details, compact: true) {
                claimsOpen = true
            }
            .accessibilityIdentifier(WalletAccessibilityID.presentationClaimsToggle(option.selection.id))
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.selection.id))
        .sheet(isPresented: $claimsOpen) {
            SharingClaimsSheet(
                option: option,
                details: details,
                credentialSelected: credentialSelected,
                selectedDisclosureOptions: selection.disclosures,
                requestedDisclosureItems: requestedDisclosureItems,
                isLoading: isLoading,
                isReadOnly: isReadOnly,
                onToggleDisclosure: onToggleDisclosure,
                onDismiss: { claimsOpen = false }
            )
        }
    }
}

/// Scrollable claim review the user can leave without changing the Share decision.
private struct SharingClaimsSheet: View {
    let option: PresentationCredentialOption
    let details: CredentialDetails
    let credentialSelected: Bool
    let selectedDisclosureOptions: Set<PresentationDisclosureSelection>
    let requestedDisclosureItems: [ClaimItem]
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 8) {
                Text(details.cardSummary.title)
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button("Close", action: onDismiss)
                    .accessibilityIdentifier(WalletAccessibilityID.presentationClaimsClose)
            }
            .padding(.horizontal)
            .padding(.top)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    SharingClaimsIssuerRow(details: details)
                    if option.disclosures.isEmpty {
                        Text("No additional claims to review")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        DisclosureList(
                            option: option,
                            credentialSelected: credentialSelected,
                            selectedDisclosureOptions: selectedDisclosureOptions,
                            requestedDisclosureItems: requestedDisclosureItems,
                            isLoading: isLoading,
                            isReadOnly: isReadOnly,
                            onToggleDisclosure: onToggleDisclosure
                        )
                    }
                }
                .padding()
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.presentationClaimsDialog)
    }
}

private struct SharingClaimsIssuerRow: View {
    let details: CredentialDetails

    var body: some View {
        if let issuerDisplay = details.issuerDisplay {
            let issuer = details.issuer?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            MetadataIdentityView(
                display: issuerDisplay,
                fallbackName: details.cardSummary.issuer,
                supportingText: issuer.isEmpty || issuer == issuerDisplay.name ? nil : issuer
            )
        } else {
            Text("Issuer: \(details.cardSummary.issuer)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

public struct ReviewCheckboxToggleStyle: ToggleStyle {
    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                    .font(.title2)
                    .foregroundStyle(configuration.isOn ? Color.accentColor : Color.secondary)
                configuration.label
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(configuration.isOn ? [.isSelected] : [])
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

/// The supported transport-specific presentations for the shared review actions.
public enum ReviewActionPresentation {
    case sharing
    case proximity

    var submitTitle: String {
        "Share"
    }

    var rejectTitle: String {
        self == .sharing ? "Reject" : "Decline"
    }

    var cancelTitle: String? {
        self == .sharing ? nil : "Cancel"
    }

    var submitAccessibilityIdentifier: String {
        self == .sharing
            ? WalletAccessibilityID.presentationSubmitButton
            : WalletAccessibilityID.proximityApproveButton
    }

    var rejectAccessibilityIdentifier: String {
        self == .sharing
            ? WalletAccessibilityID.presentationRejectButton
            : WalletAccessibilityID.proximityDeclineButton
    }

    var cancelAccessibilityIdentifier: String {
        self == .sharing
            ? WalletAccessibilityID.presentationCancelButton
            : WalletAccessibilityID.proximityCancelButton
    }
}

/// Presentation actions, with transport-specific labels and accessibility identifiers when needed.
public struct ReviewActions: View {
    @Environment(\.walletDemoBranding) private var branding
    let selectionComplete: Bool
    let isLoading: Bool
    let onSubmit: () -> Void
    let onReject: (() -> Void)?
    let onCancel: () -> Void
    let presentation: ReviewActionPresentation

    public init(
        selectionComplete: Bool,
        isLoading: Bool,
        onSubmit: @escaping () -> Void,
        onReject: (() -> Void)?,
        onCancel: @escaping () -> Void,
        presentation: ReviewActionPresentation = .sharing
    ) {
        self.selectionComplete = selectionComplete
        self.isLoading = isLoading
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
        self.presentation = presentation
    }

    public var body: some View {
        HStack(spacing: 10) {
            actionButtons
        }
    }

    @ViewBuilder
    private var actionButtons: some View {
        Button(presentation.submitTitle, action: onSubmit)
            .buttonStyle(.borderedProminent)
            .tint(branding.primary)
            .disabled(isLoading || !selectionComplete)
            .accessibilityIdentifier(presentation.submitAccessibilityIdentifier)

        // By default this says "Cancel review" where a protocol-level Reject also exists, so the two
        // ways of declining cannot be mistaken for each other. Transports may supply a more precise label.
        Button(presentation.cancelTitle ?? (onReject == nil ? "Cancel" : "Cancel review"), action: onCancel)
            .buttonStyle(.bordered)
            .disabled(isLoading)
            .accessibilityIdentifier(presentation.cancelAccessibilityIdentifier)

        if let onReject {
            Button(presentation.rejectTitle, action: onReject)
                .buttonStyle(.bordered)
                .disabled(isLoading)
                .accessibilityIdentifier(presentation.rejectAccessibilityIdentifier)
        }
    }
}
