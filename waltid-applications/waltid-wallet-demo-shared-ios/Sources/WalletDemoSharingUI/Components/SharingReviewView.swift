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
    private let showsActions: Bool
    private let context: ReviewSurfaceContext
    private let onToggleCredential: (PresentationCredentialSelection) -> Void
    private let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    private let onCredentialSelected: ((String) -> Void)?
    private let onSubmit: () -> Void
    private let onReject: (() -> Void)?
    private let onCancel: () -> Void

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
    ///   - onReject: Sends a protocol-level refusal to the Verifier. Pass `nil` for transports with
    ///     no such message - the platform Digital Credentials APIs return a cancellation instead, and
    ///     offering both Reject and Cancel there would promise the Verifier gets told two different
    ///     things.
    public init(
        review: SharingReviewModel,
        selection: SharingSelection,
        selectionComplete: Bool,
        isLoading: Bool = false,
        isReadOnly: Bool = false,
        showsActions: Bool = true,
        context: ReviewSurfaceContext = .selectedForSharing,
        onToggleCredential: @escaping (PresentationCredentialSelection) -> Void,
        onToggleDisclosure: @escaping (PresentationDisclosureSelection) -> Void,
        onCredentialSelected: ((String) -> Void)? = nil,
        onSubmit: @escaping () -> Void,
        onReject: (() -> Void)? = nil,
        onCancel: @escaping () -> Void
    ) {
        self.review = review
        self.selection = selection
        self.selectionComplete = selectionComplete
        self.isLoading = isLoading
        self.isReadOnly = isReadOnly
        self.showsActions = showsActions
        self.context = context
        self.onToggleCredential = onToggleCredential
        self.onToggleDisclosure = onToggleDisclosure
        self.onCredentialSelected = onCredentialSelected
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ReviewIslandNavigationView(
                islands: review.reviewIslands(context: context),
                showsModelExpandedValues: { island in
                    island.kind != .credential && island.kind != .information
                }
            ) { island in
                expandedContent(for: island)
            }

            if showsActions && !isReadOnly {
                SharingReviewActions(
                    selectionComplete: selectionComplete,
                    isLoading: isLoading,
                    onSubmit: onSubmit,
                    onReject: onReject,
                    onCancel: onCancel
                )
            }
        }
    }

    @ViewBuilder
    private func expandedContent(for island: ReviewIsland) -> some View {
        switch island.kind {
        case .verifier:
            VerifierReviewFacts(request: review.request)
        case .credential:
            VStack(alignment: .leading, spacing: 12) {
                ForEach(review.credentialOptions) { option in
                    CredentialReviewCard(
                        option: option,
                        selection: selection,
                        isLoading: isLoading,
                        isReadOnly: isReadOnly,
                        onToggleCredential: onToggleCredential,
                        onCredentialSelected: onCredentialSelected
                    )
                }
            }
        case .information:
            VStack(alignment: .leading, spacing: 12) {
                ForEach(review.credentialOptions.filter { !$0.disclosures.isEmpty }) { option in
                    DisclosureList(
                        option: option,
                        credentialSelected: selection.credentials.contains(option.selection),
                        selectedDisclosureOptions: selection.disclosures,
                        requestedDisclosureItems: requestedDisclosureItems(for: option),
                        isLoading: isLoading,
                        isReadOnly: isReadOnly,
                        onToggleDisclosure: onToggleDisclosure
                    )
                }
            }
        case .issuer, .purposeAndTransaction, .requiredAction:
            EmptyView()
        }
    }

    private func requestedDisclosureItems(for option: PresentationCredentialOption) -> [ClaimItem] {
        CredentialDisplayNormalizer.details(for: option).groups
            .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
            .items ?? []
    }
}

/// One offered credential: what it is, and what would be disclosed from it.
struct CredentialReviewCard: View {
    let option: PresentationCredentialOption
    let selection: SharingSelection
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let onCredentialSelected: ((String) -> Void)?

    var body: some View {
        let details = CredentialDisplayNormalizer.details(for: option)
        VStack(alignment: .leading, spacing: 10) {
            if !isReadOnly {
                Toggle(isOn: Binding(get: {
                    selection.credentials.contains(option.selection)
                }, set: { _ in
                    onToggleCredential(option.selection)
                })) {
                    Text(option.userFacingLabel)
                        .font(.subheadline.weight(.medium))
                }
                .disabled(isLoading)
                .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.selection.id))
            }

            if let onCredentialSelected {
                CredentialCardButton(details: details, showProtocolDetails: false) {
                    onCredentialSelected(details.id)
                }
                .padding(.leading, isReadOnly ? 0 : 28)
            } else {
                CredentialCardView(details: details, showProtocolDetails: false)
                    .padding(.leading, isReadOnly ? 0 : 28)
            }

            Divider()
        }
    }
}

private struct VerifierReviewFacts: View {
    let request: SharingRequest

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let readerTrust = request.readerTrust {
                let fact = readerTrust.userFacingFact
                VStack(alignment: .leading, spacing: 2) {
                    Text(fact.0).font(.subheadline.weight(.medium))
                    Text(fact.1).font(.caption).foregroundStyle(.secondary)
                }
                .accessibilityIdentifier(WalletAccessibilityID.presentationReaderTrustSection)
            }
            Text(request.responseProtection.userFacingExplanation)
                .font(.caption)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier(WalletAccessibilityID.presentationResponseProtectionSection)
        }
    }
}

private extension SharingReaderTrust {
    var userFacingFact: (String, String) {
        switch self {
        case .notAuthenticated:
            return ("Reader not authenticated", "The request carried no reader signature.")
        case .pendingVerification:
            return ("Reader authentication checked before sharing", "Nothing is sent if verification fails.")
        case .untrusted(let reason):
            return ("Reader identity not trusted by this wallet", reason)
        case .trusted(let identity):
            return ("Trusted reader", identity)
        }
    }
}

private extension SharingResponseProtection {
    var userFacingExplanation: String {
        switch self {
        case .none:
            return "The request does not require an encrypted response."
        case .encrypted(let mechanism, _, _, _, _):
            switch mechanism {
            case .jwe: return "The response is encrypted for the Verifier."
            case .dcAPIJWT: return "The response is encrypted for platform delivery."
            case .annexCHPKE: return "The response is protected for this reader session."
            }
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
public struct SharingReviewActions: View {
    let selectionComplete: Bool
    let isLoading: Bool
    let onSubmit: () -> Void
    let onReject: (() -> Void)?
    let onCancel: () -> Void

    public init(
        selectionComplete: Bool,
        isLoading: Bool,
        onSubmit: @escaping () -> Void,
        onReject: (() -> Void)?,
        onCancel: @escaping () -> Void
    ) {
        self.selectionComplete = selectionComplete
        self.isLoading = isLoading
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
    }

    public var body: some View {
        VStack(alignment: .trailing, spacing: 8) {
            Button(action: onSubmit) {
                Text("Share information")
                    .frame(maxWidth: .infinity)
            }
                .buttonStyle(.borderedProminent)
                .tint(.waltBlue)
                .disabled(isLoading || !selectionComplete)
                .accessibilityIdentifier(WalletAccessibilityID.presentationSubmitButton)

            HStack(spacing: 10) {
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
}
