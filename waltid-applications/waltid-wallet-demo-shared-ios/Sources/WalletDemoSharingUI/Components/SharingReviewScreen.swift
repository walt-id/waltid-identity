import SwiftUI
import WalletSDK

/// A sharing review for the bounded container supplied by an app or operating-system provider host.
/// Long requests scroll independently of the pinned actions, including at large text sizes.
///
/// It adds only what a standalone surface needs - a heading naming the request, a preparing state and
/// a failure state - on top of ``SharingReviewView``. The
/// review content itself is the same one the in-app flow renders, so a request cannot be described
/// one way inside the app and another way in a provider extension.
///
/// Selection is owned by the caller rather than by this screen, because the caller is what has to
/// submit it: in a provider extension the transport, the retained request handle and the platform
/// result all live outside the view.
public struct SharingReviewScreen: View {
    private let title: String
    private let review: SharingReviewModel?
    private let selection: SharingSelection
    private let selectionComplete: Bool
    private let failure: String?
    private let isSubmitting: Bool
    private let paymentReview: PaymentReviewState
    private let onToggleCredential: (PresentationCredentialSelection) -> Void
    private let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    private let onSubmit: () -> Void
    private let onReject: (() -> Void)?
    private let onCancel: () -> Void

    /// Renders a standalone sharing review.
    ///
    /// - Parameters:
    ///   - title: Heading naming the kind of request being reviewed.
    ///   - review: What is being asked, or `nil` while the request is still being prepared.
    ///   - failure: Why the flow cannot continue, when it cannot.
    ///   - isSubmitting: Whether a response is being built, which disables every action.
    ///   - onReject: Protocol-level refusal, or `nil` for transports with no such message.
    public init(
        title: String,
        review: SharingReviewModel?,
        selection: SharingSelection,
        selectionComplete: Bool,
        failure: String? = nil,
        isSubmitting: Bool = false,
        paymentReview: PaymentReviewState = .notRequired,
        onToggleCredential: @escaping (PresentationCredentialSelection) -> Void,
        onToggleDisclosure: @escaping (PresentationDisclosureSelection) -> Void,
        onSubmit: @escaping () -> Void,
        onReject: (() -> Void)? = nil,
        onCancel: @escaping () -> Void
    ) {
        self.title = title
        self.review = review
        self.selection = selection
        self.selectionComplete = selectionComplete
        self.failure = failure
        self.isSubmitting = isSubmitting
        self.paymentReview = paymentReview
        self.onToggleCredential = onToggleCredential
        self.onToggleDisclosure = onToggleDisclosure
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
    }

    public var body: some View {
        WalletNavigationContainer {
            WalletReviewScaffold(showsActions: review != nil || failure != nil) {
                content
            } actions: {
                if review != nil && failure == nil {
                    ReviewActions(
                        selectionComplete: selectionComplete,
                        isLoading: isSubmitting,
                        onSubmit: onSubmit,
                        onReject: onReject,
                        onCancel: onCancel,
                        paymentReview: paymentReview, showCancelWithReject: false
                    )
                } else if let failure {
                    Text(failure).font(.subheadline).foregroundStyle(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: onCancel) { Image(systemName: "xmark").frame(minWidth: 44, minHeight: 44) }
                        .accessibilityLabel("Close request").disabled(isSubmitting)
                        .accessibilityIdentifier("wallet.flowBack")
                }
            }
        }
        .interactiveDismissDisabled(isSubmitting)
    }

    @ViewBuilder
    private var content: some View {
        if failure != nil {
            Label("Unable to share", systemImage: "exclamationmark.shield")
                .font(.headline).accessibilityIdentifier(WalletAccessibilityID.presentationError)
        } else if let review {
            SharingReviewView(
                review: review,
                selection: selection,
                selectionComplete: selectionComplete,
                isLoading: isSubmitting,
                onToggleCredential: onToggleCredential,
                onToggleDisclosure: onToggleDisclosure,
                onSubmit: onSubmit,
                onReject: onReject,
                onCancel: onCancel,
                compact: true,
                showActions: false,
                paymentReview: paymentReview
            )
        } else {
            // No request content is shown while preparing: what a request asks for is only known once
            // the wallet has matched it, and a skeleton of sections here would suggest otherwise.
            ProgressView("Preparing request")
                .frame(maxWidth: .infinity, alignment: .center)
                .accessibilityIdentifier(WalletAccessibilityID.presentationPreparing)
        }
    }
}
