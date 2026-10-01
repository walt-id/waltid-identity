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
    var replacesGenericPayment: Bool { self != .notRequired }
}

struct PaymentConsentView: View {
    let state: PaymentReviewState
    @State private var detailsExpanded = false

    var body: some View {
        switch state {
        case .notRequired: EmptyView()
        case .loading: ProgressView("Loading payment instructions…").accessibilityIdentifier("payment-consent-loading")
        case .blocked(let message): Text(message).foregroundStyle(.red).accessibilityIdentifier("payment-consent-blocked")
        case .ready(let consent):
            VStack(alignment: .leading, spacing: 12) {
                Text(consent.title ?? String(localized: "Review payment", bundle: .module)).font(.title2)
                if consent.requiresUnsignedRequestWarning {
                    Text("This payment request is unsigned. Confirm only if you recognize the requester and approve the payment below.")
                        .foregroundStyle(.red).accessibilityIdentifier("payment-unsigned-warning")
                }
                if let hint = consent.securityHint { Text(hint).accessibilityIdentifier("payment-security-hint") }
                fields(consent.fields.filter { $0.placement == .prominent }) { field in
                    fieldView(field, prominent: true).padding().frame(maxWidth: .infinity, alignment: .leading)
                        .background(.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                }
                fields(consent.fields.filter { $0.placement == .main }) { fieldView($0, prominent: false) }
                let details = consent.fields.filter { $0.placement == .details }
                if !details.isEmpty {
                    DisclosureGroup("Payment details", isExpanded: $detailsExpanded) {
                        fields(details) { fieldView($0, prominent: false) }
                    }.accessibilityIdentifier("payment-details-toggle")
                }
            }
            .accessibilityIdentifier("payment-consent")
            .onChange(of: consent.revision) { _ in detailsExpanded = false }
        }
    }

    private func fields<Content: View>(_ fields: [PaymentConsentField], @ViewBuilder content: @escaping (PaymentConsentField) -> Content) -> some View {
        ForEach(Array(fields.enumerated()), id: \.offset) { _, field in content(field) }
    }

    private func fieldView(_ field: PaymentConsentField, prominent: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(field.label).font(.subheadline)
            Text(field.value).font(prominent ? .title3.weight(.semibold) : .body).textSelection(.enabled)
            if let description = field.description { Text(description).font(.callout) }
        }.fixedSize(horizontal: false, vertical: true)
    }
}
