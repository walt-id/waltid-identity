import SwiftUI

/// Presentation actions, with transport-specific labels and accessibility identifiers when needed.
public struct ReviewActions: View {
    let selectionComplete: Bool
    let isLoading: Bool
    let onSubmit: () -> Void
    let onReject: (() -> Void)?
    let onCancel: () -> Void
    let presentation: ReviewActionPresentation
    let paymentReview: PaymentReviewState
    @State private var pendingUnsignedRevision: String?

    public init(
        selectionComplete: Bool,
        isLoading: Bool,
        onSubmit: @escaping () -> Void,
        onReject: (() -> Void)?,
        onCancel: @escaping () -> Void,
        presentation: ReviewActionPresentation = .sharing,
        paymentReview: PaymentReviewState = .notRequired
    ) {
        self.selectionComplete = selectionComplete
        self.isLoading = isLoading
        self.onSubmit = onSubmit
        self.onReject = onReject
        self.onCancel = onCancel
        self.presentation = presentation
        self.paymentReview = paymentReview
    }

    public var body: some View {
        let canSubmit = !isLoading && selectionComplete && paymentReview.canConfirm
        let cancel = WalletAction(
            (onReject == nil ? paymentReview.consent?.denialAction : nil) ?? presentation.cancelTitle
                ?? (onReject == nil ? String(localized: "Cancel", bundle: .module) : String(localized: "Cancel review", bundle: .module)),
            enabled: !isLoading || presentation == .proximity, identifier: presentation.cancelAccessibilityIdentifier,
            perform: onCancel
        )
        WalletActions(
            primary: WalletAction(paymentReview.consent?.affirmativeAction ?? presentation.submitTitle,
                enabled: canSubmit,
                identifier: presentation.submitAccessibilityIdentifier, perform: {
                    if let consent = paymentReview.consent, consent.requiresUnsignedRequestWarning {
                        pendingUnsignedRevision = consent.revision
                    } else { onSubmit() }
                }),
            secondary: onReject.map { reject in
                WalletAction(paymentReview.consent?.denialAction ?? presentation.rejectTitle,
                    enabled: !isLoading, identifier: presentation.rejectAccessibilityIdentifier, perform: reject)
            } ?? cancel,
            tertiary: onReject == nil ? nil : cancel
        )
        .alert(String(localized: "Unsigned payment request", bundle: .module), isPresented: Binding(
            get: { pendingUnsignedRevision != nil },
            set: { if !$0 { pendingUnsignedRevision = nil } }
        ), presenting: pendingUnsignedRevision) { revision in
            Button(paymentReview.consent?.affirmativeAction ?? presentation.submitTitle) {
                guard canSubmit, revision == paymentReview.consent?.revision else { return }
                pendingUnsignedRevision = nil
                onSubmit()
            }.accessibilityIdentifier("payment-unsigned-confirm")
            Button(String(localized: "Back to review", bundle: .module), role: .cancel) {
                pendingUnsignedRevision = nil
            }.accessibilityIdentifier("payment-unsigned-back")
        } message: { _ in
            Text("This request has no signature to verify its sender. Do you want to proceed with the payment you reviewed?", bundle: .module)
        }
        .onChange(of: paymentReview.consent?.revision) { _ in pendingUnsignedRevision = nil }
        .onChange(of: canSubmit) { if !$0 { pendingUnsignedRevision = nil } }
    }
}
