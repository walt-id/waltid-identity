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
        VStack(alignment: .leading, spacing: 8) {
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
            VStack(alignment: .leading, spacing: 8) {
                ForEach(review.credentialOptions) { option in
                    CredentialReviewCard(
                        option: option,
                        showsCredentialName: review.credentialOptions.count > 1,
                        selection: selection,
                        isLoading: isLoading,
                        isReadOnly: isReadOnly,
                        onToggleCredential: onToggleCredential,
                        onCredentialSelected: onCredentialSelected
                    )
                }
            }
        case .information:
            VStack(alignment: .leading, spacing: 8) {
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
        case .issuer, .validityAndStatus, .purposeAndTransaction, .requiredAction:
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
    let showsCredentialName: Bool
    let selection: SharingSelection
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let onCredentialSelected: ((String) -> Void)?

    var body: some View {
        let details = CredentialDisplayNormalizer.details(for: option)
        HStack(alignment: .center, spacing: 10) {
            if !isReadOnly {
                Toggle(isOn: Binding(get: {
                    selection.credentials.contains(option.selection)
                }, set: { _ in
                    onToggleCredential(option.selection)
                })) {
                    EmptyView()
                }
                .labelsHidden()
                .disabled(isLoading)
                .accessibilityLabel(showsCredentialName ? option.userFacingLabel : "Use this credential")
                .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.selection.id))
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(showsCredentialName ? option.userFacingLabel : "Use this credential")
                    .font(.subheadline.weight(.medium))
                if showsCredentialName, let issuer = option.issuer?.presentableValue {
                    Text(issuer)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if let subject = option.subject?.presentableValue {
                    Text(subject)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if let onCredentialSelected {
                Button {
                    onCredentialSelected(details.id)
                } label: {
                    Image(systemName: "chevron.right")
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.tint)
                .accessibilityLabel("View credential details")
                .accessibilityIdentifier(WalletAccessibilityID.credentialCard(details.id))
            }
        }
        .padding(.vertical, 2)
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

    @ViewBuilder public var body: some View {
        if #available(iOS 16.0, *) {
            ViewThatFits(in: .horizontal) {
                actionRow(compact: false)
                actionRow(compact: true)
            }
        } else {
            actionRow(compact: true)
        }
    }

    private func actionRow(compact: Bool) -> some View {
        HStack(spacing: 8) {
            Button(action: onSubmit) {
                Text("Share")
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.waltBlue)
            .disabled(isLoading || !selectionComplete)
            .accessibilityLabel("Share information")
            .accessibilityIdentifier(WalletAccessibilityID.presentationSubmitButton)

            reviewAction(
                title: "Cancel",
                systemImage: "xmark",
                compact: compact,
                accessibilityLabel: "Cancel",
                identifier: WalletAccessibilityID.presentationCancelButton,
                action: onCancel
            )

            if let onReject {
                reviewAction(
                    title: "Reject",
                    systemImage: "hand.raised",
                    compact: compact,
                    accessibilityLabel: "Reject request",
                    identifier: WalletAccessibilityID.presentationRejectButton,
                    action: onReject
                )
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func reviewAction(
        title: String,
        systemImage: String,
        compact: Bool,
        accessibilityLabel: String,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            if compact {
                Image(systemName: systemImage)
                    .frame(width: 20, height: 20)
            } else {
                Text(title).lineLimit(1)
            }
        }
        .buttonStyle(.bordered)
        .disabled(isLoading)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityIdentifier(identifier)
    }
}
