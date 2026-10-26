import SwiftUI
import WalletSDK

/// Presentation state for instructions validated and localized by the shared wallet core.
public enum PaymentReviewState: Equatable {
    case notRequired
    case loading
    case ready(PaymentConsent)
    case blocked(String)

    public var canConfirm: Bool {
        switch self { case .notRequired, .ready: true; case .loading, .blocked: false }
    }
    public var consent: PaymentConsent? { if case .ready(let consent) = self { consent } else { nil } }
}

struct PaymentConsentView: View {
    let state: PaymentReviewState
    var transactions: [ClaimGroup] = []

    var body: some View {
        switch state {
        case .loading: WalletProgressRow(String(localized: "Loading payment instructions…", bundle: .module))
            .accessibilityIdentifier("payment-consent-loading")
        case .blocked(let message): Text(message).foregroundStyle(.red).accessibilityIdentifier("payment-consent-blocked")
        default: EmptyView()
        }
        ForEach(state.displays(transactions: transactions)) { PaymentReviewContent(payment: $0) }
    }
}

/// Both payment sources use the same hierarchy, field renderer and detail controls.
private struct PaymentReviewContent: View {
    let payment: PaymentDisplay

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(payment.title ?? String(localized: "Review payment", bundle: .module)).font(.title2)
            if payment.requiresUnsignedRequestWarning {
                Text("This payment request is unsigned. Confirm only if you recognize the requester and approve the payment below.", bundle: .module)
                    .foregroundStyle(.red).accessibilityIdentifier("payment-unsigned-warning")
            }
            if let hint = payment.securityHint { Text(hint).accessibilityIdentifier("payment-security-hint") }
            ForEach(payment.fields.filter { $0.placement == .prominent }) { field in
                fieldView(field, prominent: true).padding().frame(maxWidth: .infinity, alignment: .leading)
                    .background(.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
            }
            ForEach(payment.fields.filter { $0.placement == .main }) { fieldView($0, prominent: false) }
            let details = payment.fields.filter { $0.placement == .details }
            if !details.isEmpty {
                MetadataDisclosure(title: String(localized: "Payment details", bundle: .module), initiallyExpanded: false,
                    accessibilityIdentifier: "payment-details-toggle") {
                    ForEach(details) { fieldView($0, prominent: false) }
                }
            }
            if !payment.technical.isEmpty {
                MetadataDisclosure(title: String(localized: "Technical details", bundle: .module), initiallyExpanded: false) {
                    ForEach(payment.technical) { ClaimValueRow(item: $0) }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("payment-consent")
    }

    private func fieldView(_ field: PaymentDisplayField, prominent: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(field.item.label).font(.subheadline)
            ClaimValueView(value: field.item.value, path: field.item.path,
                textFont: prominent ? .title3.weight(.semibold) : .body).textSelection(.enabled)
            if let description = field.description { Text(description).font(.callout) }
        }.fixedSize(horizontal: false, vertical: true)
    }
}
