import SwiftUI
import WalletSDK

/// A compact sharing review, for hosts the operating system launched with no app chrome around
/// them. It sizes to the heading, credential, and actions rather than filling the display.
///
/// It adds only what a standalone surface needs - a heading naming the request, a preparing state and
/// a failure state - on top of ``SharingReviewView``. The
/// review content itself is the same one the in-app flow renders, so a request cannot be described
/// one way inside the app and another way in a provider extension.
///
/// Selection is owned by the caller rather than by this screen, because the caller is what has to
/// submit it: in a provider extension the transport, the retained request handle and the platform
/// result all live outside the view.
@available(iOS 16.0, *)
public struct SharingReviewScreen: View {
    private let title: String
    private let review: SharingReviewModel?
    private let selection: SharingSelection
    private let selectionComplete: Bool
    private let failure: String?
    private let isSubmitting: Bool
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
        self.onToggleCredential = onToggleCredential
        self.onToggleDisclosure = onToggleDisclosure
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 14) {
                Text(title)
                    .font(.title2.weight(.semibold))
                content
            }
            .padding(20)

            if review != nil && failure == nil {
                Divider()
                ReviewActions(
                    selectionComplete: selectionComplete,
                    isLoading: isSubmitting,
                    onSubmit: onSubmit,
                    onReject: onReject,
                    onCancel: onCancel
                )
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .frame(maxWidth: .infinity)
        .background(Color(.systemBackground))
    }

    @ViewBuilder
    private var content: some View {
        if let failure {
            SharingReviewFailureView(message: failure, onCancel: onCancel)
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
                showActions: false
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

/// Why the wallet will not continue, with the only action left.
struct SharingReviewFailureView: View {
    let message: String
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Unable to share", systemImage: "exclamationmark.shield")
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Cancel", action: onCancel)
                .buttonStyle(.bordered)
                .accessibilityIdentifier(WalletAccessibilityID.presentationCancelButton)
        }
        .accessibilityIdentifier(WalletAccessibilityID.presentationError)
    }
}
