import SwiftUI
import UIKit
import WalletSDK

private struct SubmissionBinding<Key: Equatable>: Equatable {
    struct Revision: Equatable { let credentialID: String; let storeIndex: Int?; let revision: String? }
    let review: Key
    let context: AnyHashable?
    let credentials: [Revision]
    let paymentRevision: String?
}

/// Actions shared by online, provider and nearby hosts, with one final warning gate.
public struct ReviewActions<Key: Equatable>: View {
    @Environment(\.credentialStatusUI) private var statusUI
    @StateObject private var gate = ReviewSubmissionGate<SubmissionBinding<Key>>()
    private let reviewKey: Key
    private let credentialStatuses: [ReviewCredentialStatus]
    private let selectionComplete: Bool
    private let isLoading: Bool
    private let onSubmit: ([String: String]) -> Void
    private let onReject: (() -> Void)?
    private let onCancel: () -> Void
    private let presentation: ReviewActionPresentation
    private let paymentReview: PaymentReviewState
    private let isPayment: Bool
    private let showCancelWithReject: Bool
    private let showCancel: Bool
    private let submitTitleOverride: String?

    public init(selectionComplete: Bool, isLoading: Bool, reviewKey: Key,
                credentialStatuses: [ReviewCredentialStatus], onSubmit: @escaping ([String: String]) -> Void,
                onReject: (() -> Void)?, onCancel: @escaping () -> Void,
                presentation: ReviewActionPresentation = .sharing, paymentReview: PaymentReviewState = .notRequired,
                isPayment: Bool = false, showCancelWithReject: Bool = true, showCancel: Bool = true,
                submitTitleOverride: String? = nil) {
        self.selectionComplete = selectionComplete; self.isLoading = isLoading; self.reviewKey = reviewKey
        self.credentialStatuses = credentialStatuses; self.onSubmit = onSubmit; self.onReject = onReject; self.onCancel = onCancel
        self.presentation = presentation; self.paymentReview = paymentReview; self.isPayment = isPayment
        self.showCancelWithReject = showCancelWithReject; self.showCancel = showCancel; self.submitTitleOverride = submitTitleOverride
    }

    public var body: some View {
        let revisions = statusUI.reviewedRevisions(for: credentialStatuses)
        let canSubmit = !isLoading && selectionComplete && paymentReview.canConfirm && revisions != nil
        let binding = SubmissionBinding(review: reviewKey, context: statusUI.contextKey,
            credentials: credentialStatuses.map { SubmissionBinding<Key>.Revision(credentialID: $0.credentialID,
                storeIndex: $0.storeIndex, revision: $0.presentation?.advisoryRevision) }.sorted { $0.credentialID < $1.credentialID },
            paymentRevision: paymentReview.consent?.revision)
        let ordinarySharing = presentation == .sharing && !isPayment && paymentReview.consent == nil
        let submitTitle = submitTitleOverride ?? paymentReview.consent?.affirmativeAction
            ?? (isPayment ? String(localized: "Confirm payment", bundle: .module) : presentation.submitTitle)
        let unsigned = paymentReview.consent?.requiresUnsignedRequestWarning == true
        let adverse = credentialStatuses.filter { $0.presentation?.hasAdverseStatus == true }
        let message = confirmationMessage(adverse: adverse, unsigned: unsigned)
        let cancel = WalletAction(
            (onReject == nil ? paymentReview.consent?.denialAction : nil) ?? presentation.cancelTitle
                ?? (onReject == nil ? String(localized: "Cancel", bundle: .module) : String(localized: "Cancel review", bundle: .module)),
            enabled: !isLoading || presentation == .proximity, identifier: presentation.cancelAccessibilityIdentifier, perform: onCancel)
        VStack(spacing: 8) {
            if gate.changed { Text("The review changed. Check the updated information before continuing.", bundle: .module)
                    .font(.footnote) }
            WalletActions(
                primary: WalletAction(submitTitle, enabled: canSubmit, identifier: presentation.submitAccessibilityIdentifier) {
                    guard let revisions else { return }
                    gate.begin(.init(key: binding, revisions: revisions, unsigned: unsigned, message: message,
                        affirmative: ordinarySharing ? String(localized: "Share anyway", bundle: .module) : submitTitle, submit: onSubmit),
                        needsConfirmation: unsigned || !adverse.isEmpty)
                },
                secondary: onReject.map { reject in WalletAction(paymentReview.consent?.denialAction
                    ?? (isPayment ? String(localized: "Decline payment", bundle: .module) : presentation.rejectTitle),
                    enabled: !isLoading, identifier: presentation.rejectAccessibilityIdentifier, perform: reject) }
                    ?? (showCancel ? cancel : nil),
                tertiary: onReject == nil || !showCancelWithReject || !showCancel ? nil : cancel)
        }
        .alert(gate.pending?.unsigned == true ? String(localized: "Unsigned payment request", bundle: .module)
               : ordinarySharing ? String(localized: "Share with status issues?", bundle: .module)
               : String(localized: "Credential status warning", bundle: .module),
               isPresented: Binding(get: { gate.pending != nil }, set: { if !$0 { gate.dismiss() } }), presenting: gate.pending) { attempt in
            Button(attempt.affirmative) { gate.confirm(attempt) }
                .accessibilityIdentifier(attempt.unsigned ? "payment-unsigned-confirm" : "credential-status-confirm")
            Button(String(localized: "Back to review", bundle: .module), role: .cancel) { gate.dismiss() }
                .accessibilityIdentifier(attempt.unsigned ? "payment-unsigned-back" : "credential-status-back")
        } message: { Text($0.message) }
        .onAppear { gate.update(binding, enabled: canSubmit) }
        .onChange(of: binding) { gate.update($0, enabled: canSubmit) }
        .onChange(of: canSubmit) { gate.update(binding, enabled: $0) }
        .onChange(of: gate.changed) { changed in
            if changed { UIAccessibility.post(notification: .announcement,
                argument: String(localized: "The review changed. Check the updated information before continuing.", bundle: .module)) }
        }
        .onDisappear { gate.close() }
    }

    private func confirmationMessage(adverse: [ReviewCredentialStatus], unsigned: Bool) -> String {
        var parts: [String] = []
        if unsigned { parts.append(String(localized: "This request has no signature to verify its sender. Do you want to proceed with the payment you reviewed?", bundle: .module)) }
        if adverse.count == 1, let value = adverse.first, let status = value.presentation {
            let name = value.issuer.flatMap { $0.isEmpty ? nil : "\(value.title) (\($0))" } ?? value.title
            parts.append(String(localized: "\(name): \(status.statusTitle.lowercased()). The recipient may reject it.", bundle: .module))
        } else if adverse.count > 1 {
            parts.append(String(localized: "\(adverse.count) selected credentials have adverse status. The recipient may reject them.", bundle: .module))
        }
        return parts.joined(separator: "\n\n")
    }
}
