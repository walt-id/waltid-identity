import SwiftUI
import WalletDemoSharingUI
import WalletSDK

/// Hosts the wallet's shared sharing review for one Annex C presentation, in either demo's provider
/// extension.
///
/// This view is the whole provider UI: it owns the Apple request context and the two-stage
/// submission through ``AnnexCPresentationModel``, and renders the same review the in-app OpenID4VP
/// flow renders. Nothing here formats claims, names the requester or describes reader trust - all of
/// that is the shared review's job, so the two demos and the two transports cannot diverge on what a
/// request is saying.
@available(iOS 26.0, *)
public struct AnnexCReviewView: View {
    @StateObject private var model: AnnexCPresentationModel

    /// Creates the review UI over a prepared presentation model.
    public init(model: AnnexCPresentationModel) {
        _model = StateObject(wrappedValue: model)
    }

    /// Creates the review UI for one Apple request context.
    ///
    /// - Parameters:
    ///   - context: Apple's request context.
    ///   - makeWallet: Opens the wallet shared with the host app.
    public init(
        context: any AnnexCRequestContext,
        makeWallet: @escaping @Sendable () async throws -> any AnnexCPresentationWallet
    ) {
        self.init(model: AnnexCPresentationModel(context: context, makeWallet: makeWallet))
    }

    public var body: some View {
        SharingReviewScreen(
            title: "Share documents",
            review: model.review,
            selection: model.selection,
            selectionComplete: model.hasCompleteSelection,
            failure: model.failure,
            isSubmitting: model.isSubmitting,
            onToggleCredential: model.toggleCredential,
            onToggleDisclosure: model.toggleDisclosure,
            onSubmit: { Task { await model.submit() } },
            // No Reject action: IdentityDocumentServices has no way to tell the reader it was
            // refused, so cancelling is the only truthful decline. Offering both would promise the
            // requester learns something it never learns.
            onReject: nil,
            onCancel: model.cancel
        )
        .task { await model.prepare() }
    }
}
