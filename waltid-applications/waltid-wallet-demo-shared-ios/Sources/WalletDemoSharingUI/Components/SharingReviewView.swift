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
    private let paymentReview: PaymentReviewState
    @State private var credentialDetails: [CredentialDetails] = []

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
        showActions: Bool = true,
        paymentReview: PaymentReviewState = .notRequired
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
        self.paymentReview = paymentReview
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SharingRequestSections(request: review.request, replacesGenericPayment: paymentReview.replacesGenericPayment)
            PaymentConsentView(state: paymentReview)

            if !review.credentialOptions.isEmpty && credentialDetails.isEmpty {
                ProgressView("Loading credentials…")
            }

            WalletSection(compact ? "Requested credentials" : "Select credentials to share") {
                VStack(spacing: 0) {
                    if review.credentialOptions.isEmpty {
                        Text("No credentials available").font(.caption).foregroundStyle(.secondary).padding(16)
                    }
                    ForEach(Array(review.credentialOptions.enumerated()), id: \.element.id) { index, option in
                        if let details = credentialDetails.first(where: { $0.id == option.selection.id }) {
                            if index > 0 { Divider() }
                            SharingCredentialRow(option: option, details: details, selection: selection,
                                isLoading: isLoading, isReadOnly: isReadOnly,
                                onToggleCredential: onToggleCredential, onToggleDisclosure: onToggleDisclosure)
                        }
                    }
                }
            }

            if !isReadOnly && showActions {
                ReviewActions(
                    selectionComplete: selectionComplete,
                    isLoading: isLoading,
                    onSubmit: onSubmit,
                    onReject: onReject,
                    onCancel: onCancel,
                    paymentReview: paymentReview
                )
            }
        }
        .preference(key: SharingReviewReadinessKey.self, value: Set(credentialDetails.map(\.id)))
        .task(id: review.credentialOptions) {
            credentialDetails = []
            let snapshot = await CredentialDisplayNormalizer.details(for: review.credentialOptions)
            guard !Task.isCancelled else { return }
            credentialDetails = snapshot
        }
    }
}

/// Emitted after the normalized credential rows enter layout; hosted previews can await real content.
struct SharingReviewReadinessKey: PreferenceKey {
    static let defaultValue: Set<String> = []
    static func reduce(value: inout Set<String>, nextValue: () -> Set<String>) { value.formUnion(nextValue()) }
}
