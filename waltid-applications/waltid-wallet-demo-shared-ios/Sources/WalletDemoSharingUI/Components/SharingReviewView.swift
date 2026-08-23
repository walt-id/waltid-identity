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
    private let reviewRoute: Binding<ReviewRoute>?
    private let showsTechnicalHeader: Bool
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
        reviewRoute: Binding<ReviewRoute>? = nil,
        showsTechnicalHeader: Bool = true,
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
        self.reviewRoute = reviewRoute
        self.showsTechnicalHeader = showsTechnicalHeader
        self.onToggleCredential = onToggleCredential
        self.onToggleDisclosure = onToggleDisclosure
        self.onCredentialSelected = onCredentialSelected
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
    }

    public var body: some View {
        let islands = review.reviewIslands(context: context)
        let credentialIslands = islands.filter { $0.kind == .credential }
        let optionByIslandID = Dictionary(
            uniqueKeysWithValues: zip(credentialIslands, review.credentialOptions).map { ($0.id, $1) }
        )
        VStack(alignment: .leading, spacing: 8) {
            ReviewIslandNavigationView(
                islands: islands,
                showsModelExpandedValues: { island in
                    island.kind != .credential
                },
                hasCustomExpandedContent: { island in
                    if island.kind == .verifier { return true }
                    guard island.kind == .credential, let option = optionByIslandID[island.id] else {
                        return false
                    }
                    return !option.disclosures.isEmpty || onCredentialSelected != nil
                },
                route: reviewRoute,
                showsTechnicalHeader: showsTechnicalHeader,
                headerContent: { island in
                    if let option = optionByIslandID[island.id] {
                        CredentialSelectionControl(
                            option: option,
                            style: review.selectionStyle(for: option),
                            selected: selection.credentials.contains(option.selection),
                            enabled: !isLoading,
                            readOnly: isReadOnly,
                            onToggleCredential: onToggleCredential
                        )
                    }
                }
            ) { island in
                expandedContent(for: island, option: optionByIslandID[island.id])
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
    private func expandedContent(
        for island: ReviewIsland,
        option: PresentationCredentialOption?
    ) -> some View {
        switch island.kind {
        case .verifier:
            VerifierReviewFacts(request: review.request)
        case .credential:
            if let option {
                CredentialIslandExpandedContent(
                        option: option,
                        selection: selection,
                        isLoading: isLoading,
                        isReadOnly: isReadOnly,
                        requestedDisclosureItems: requestedDisclosureItems(for: option),
                        onToggleDisclosure: onToggleDisclosure,
                        onCredentialSelected: onCredentialSelected
                    )
            }
        case .issuer, .information, .validityAndStatus, .purposeAndTransaction, .requiredAction:
            EmptyView()
        }
    }

    private func requestedDisclosureItems(for option: PresentationCredentialOption) -> [ClaimItem] {
        CredentialDisplayNormalizer.details(for: option).groups
            .first { $0.title == CredentialDisplayVocabulary.requestedDisclosuresTitle }?
            .items ?? []
    }
}

/// One credential's requested fields and optional route to its stored details.
private struct CredentialIslandExpandedContent: View {
    let option: PresentationCredentialOption
    let selection: SharingSelection
    let isLoading: Bool
    let isReadOnly: Bool
    let requestedDisclosureItems: [ClaimItem]
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    let onCredentialSelected: ((String) -> Void)?

    var body: some View {
        let details = CredentialDisplayNormalizer.details(for: option)
        VStack(alignment: .leading, spacing: 8) {
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
            }

            if let onCredentialSelected {
                HStack {
                    Text("View credential")
                        .font(.subheadline.weight(.medium))
                    Spacer()
                    Image(systemName: "chevron.right")
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
                .onTapGesture { onCredentialSelected(details.id) }
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("View credential")
                .accessibilityIdentifier(WalletAccessibilityID.credentialCard(details.id))
                .accessibilityAction { onCredentialSelected(details.id) }
            }
        }
    }
}

private enum ReviewSelectionStyle {
    case checkbox
    case radio
    case included
}

private extension SharingReviewModel {
    func selectionStyle(for option: PresentationCredentialOption) -> ReviewSelectionStyle {
        if option.multiple { return .checkbox }
        let candidateCount = credentialOptions.filter { $0.queryID == option.queryID }.count
        let belongsToAlternative = credentialRequirements.contains { requirement in
            requirement.options.count > 1 && requirement.options.contains { $0.contains(option.queryID) }
        }
        return candidateCount > 1 || belongsToAlternative ? .radio : .included
    }
}

private struct CredentialSelectionControl: View {
    let option: PresentationCredentialOption
    let style: ReviewSelectionStyle
    let selected: Bool
    let enabled: Bool
    let readOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void

    private var isInteractive: Bool { enabled && !readOnly && style != .included }

    var body: some View {
        Group {
            if isInteractive {
                Button { onToggleCredential(option.selection) } label: { indicator }
                    .buttonStyle(.plain)
            } else {
                indicator
            }
        }
        .accessibilityElement()
        .accessibilityLabel(accessibilityLabel)
        .accessibilityValue(selected ? (style == .included ? "Included" : "Selected") : "Not selected")
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.selection.id))
    }

    private var accessibilityLabel: String {
        switch style {
        case .checkbox: return "Include \(option.userFacingLabel)"
        case .radio: return "Select \(option.userFacingLabel)"
        case .included: return "\(option.userFacingLabel) included"
        }
    }

    private var indicator: some View {
        ReviewSelectionIndicator(style: style, selected: selected)
            .frame(width: 22, height: 22)
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
    }
}

private struct ReviewSelectionIndicator: View {
    let style: ReviewSelectionStyle
    let selected: Bool

    var body: some View {
        ZStack {
            if style == .radio {
                Circle()
                    .stroke(Color.accentColor, lineWidth: 2)
                if selected {
                    Circle()
                        .fill(Color.accentColor)
                        .padding(6)
                }
            } else {
                RoundedRectangle(cornerRadius: 6)
                    .fill(selected ? Color.accentColor : Color.clear)
                RoundedRectangle(cornerRadius: 6)
                    .stroke(Color.accentColor, lineWidth: 2)
                if selected {
                    Image(systemName: "checkmark")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                }
            }
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
            ForEach(Array(option.disclosures.enumerated()), id: \.element.id) { index, disclosure in
                let selection = PresentationDisclosureSelection(
                    queryID: option.queryID,
                    credentialID: option.credentialID,
                    path: disclosure.path
                )

                HStack(alignment: .top, spacing: 8) {
                    let selected = disclosure.selectable
                        ? selectedDisclosureOptions.contains(selection)
                        : credentialSelected
                    if disclosure.selectable && !isReadOnly {
                        Button { onToggleDisclosure(selection) } label: {
                            ReviewSelectionIndicator(style: .checkbox, selected: selected)
                                .frame(width: 22, height: 22)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(isLoading || !credentialSelected)
                        .accessibilityLabel("Include \(disclosure.name ?? disclosure.path)")
                        .accessibilityValue(selected ? "Selected" : "Not selected")
                        .accessibilityIdentifier(WalletAccessibilityID.presentationDisclosureToggle(selection.id))
                    } else {
                        ReviewSelectionIndicator(style: .included, selected: selected)
                            .frame(width: 22, height: 22)
                            .frame(width: 44, height: 44)
                            .accessibilityLabel("\(disclosure.name ?? disclosure.path) included")
                            .accessibilityValue(selected ? "Included" : "Not included")
                            .accessibilityIdentifier(WalletAccessibilityID.presentationDisclosureToggle(selection.id))
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        disclosureLabel(index: index, disclosure: disclosure)
                        Text(disclosure.disclosureStatusText)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.vertical, 2)
                .frame(maxWidth: .infinity, alignment: .leading)
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
        HStack(spacing: 8) {
            Button(action: onSubmit) {
                Text("Share")
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(WalletPrimaryButtonStyle())
            .frame(maxWidth: .infinity)
            .disabled(isLoading || !selectionComplete)
            .accessibilityLabel("Share information")
            .accessibilityIdentifier(WalletAccessibilityID.presentationSubmitButton)

            reviewAction(
                title: "Cancel",
                accessibilityLabel: "Cancel",
                identifier: WalletAccessibilityID.presentationCancelButton,
                action: onCancel
            )

            if let onReject {
                reviewAction(
                    title: "Reject",
                    accessibilityLabel: "Reject request",
                    identifier: WalletAccessibilityID.presentationRejectButton,
                    action: onReject
                )
            }
        }
        .frame(height: 48)
        .frame(maxWidth: .infinity)
    }

    private func reviewAction(
        title: String,
        accessibilityLabel: String,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(WalletSecondaryButtonStyle())
        .frame(maxWidth: .infinity)
        .disabled(isLoading)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityIdentifier(identifier)
    }
}
