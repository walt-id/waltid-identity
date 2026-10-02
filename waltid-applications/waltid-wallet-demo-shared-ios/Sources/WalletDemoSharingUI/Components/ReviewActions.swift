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
        let cancel = WalletAction(
            (onReject == nil ? paymentReview.consent?.denialAction : nil) ?? presentation.cancelTitle
                ?? (onReject == nil ? String(localized: "Cancel", bundle: .module) : String(localized: "Cancel review", bundle: .module)),
            enabled: !isLoading || presentation == .proximity, identifier: presentation.cancelAccessibilityIdentifier,
            perform: onCancel
        )
        WalletActions(
            primary: WalletAction(paymentReview.consent?.affirmativeAction ?? presentation.submitTitle,
                enabled: !isLoading && selectionComplete && paymentReview.canConfirm,
                identifier: presentation.submitAccessibilityIdentifier, perform: onSubmit),
            secondary: onReject.map { reject in
                WalletAction(paymentReview.consent?.denialAction ?? presentation.rejectTitle,
                    enabled: !isLoading, identifier: presentation.rejectAccessibilityIdentifier, perform: reject)
            } ?? cancel,
            tertiary: onReject == nil ? nil : cancel
        )
    }
}
