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
    var detailsInitiallyExpanded = false

    var body: some View {
        switch state {
        case .loading: WalletProgressRow(String(localized: "Loading payment instructions…", bundle: .module))
            .accessibilityIdentifier("payment-consent-loading")
        case .blocked(let message): Text(message).foregroundStyle(.red).accessibilityIdentifier("payment-consent-blocked")
        default: EmptyView()
        }
        ForEach(state.displays(transactions: transactions)) {
            PaymentReviewContent(payment: $0, detailsInitiallyExpanded: detailsInitiallyExpanded)
        }
    }
}

/// Both payment sources use the same hierarchy, field renderer and detail controls.
private struct PaymentReviewContent: View {
    let payment: PaymentDisplay
    var detailsInitiallyExpanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(payment.title ?? String(localized: "Review payment", bundle: .module)).font(.title2)
            if payment.requiresUnsignedRequestWarning {
                Text("This payment request is unsigned. Confirm only if you recognize the requester and approve the payment below.", bundle: .module)
                    .foregroundStyle(.red).accessibilityIdentifier("payment-unsigned-warning")
            }
            if let hint = payment.securityHint { Text(hint).accessibilityIdentifier("payment-security-hint") }
            let fields = payment.fields.filter { $0.placement != .omitted }
            let payee = fields.filter { $0.group == .payee }
            if !payee.isEmpty {
                PaymentPayee(fields: payee, detailsInitiallyExpanded: detailsInitiallyExpanded)
            }
            let money = fields.filter { $0.group == .amount && $0.placement != .details }
                .sorted { ($0.paymentPath == ["amount"] ? 0 : 1) < ($1.paymentPath == ["amount"] ? 0 : 1) }
            if !money.isEmpty { PaymentMoney(fields: money) }
            let remaining = fields.filter { field in field.group != .payee && !money.contains(where: { $0.id == field.id }) }
            ForEach(remaining.filter { $0.placement == .prominent }) { field in
                fieldView(field, prominent: true).padding().frame(maxWidth: .infinity, alignment: .leading)
                    .background(.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
            }
            let main = remaining.filter { $0.placement == .main }
            let timing = main.filter { $0.group == .timing }
            if !timing.isEmpty {
                Text("Payment timing", bundle: .module).font(.headline)
                ForEach(timing) { fieldView($0, prominent: false) }
            }
            ForEach(main.filter { $0.group != .timing }) { fieldView($0, prominent: false) }
            let details = remaining.filter { $0.placement == .details }
            if !details.isEmpty {
                MetadataDisclosure(title: String(localized: "Payment details", bundle: .module), initiallyExpanded: detailsInitiallyExpanded,
                    accessibilityIdentifier: "payment-details-toggle") {
                    let provider = details.filter { $0.group == .provider }
                    if !provider.isEmpty {
                        Text("Payment service provider", bundle: .module).font(.headline)
                        ForEach(provider) { fieldView($0, prominent: false) }
                    }
                    ForEach(details.filter { $0.group != .provider }) { fieldView($0, prominent: false) }
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

}

private struct PaymentMoney: View {
    let fields: [PaymentDisplayField]

    var body: some View {
        Group {
            if #available(iOS 16, *) {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .top, spacing: 16) { values }.fixedSize(horizontal: true, vertical: false)
                    VStack(alignment: .leading, spacing: 12) { values }
                }
            } else {
                LegacyWalletAdaptiveRow(spacing: 16, verticalSpacing: 12, alignment: .leading, verticalAlignment: .top) { values }
            }
        }.padding().frame(maxWidth: .infinity, alignment: .leading)
            .background(.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
    }

    private var values: some View {
        ForEach(fields) { fieldView($0, prominent: $0.placement == .prominent) }
    }
}

private func fieldView(_ field: PaymentDisplayField, prominent: Bool) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(field.item.label).font(.subheadline)
        if field.isWebsite, let value = field.item.rawValue, let url = paymentWebsite(value) {
            Link(destination: url) { Text(value + " ↗") }.frame(minHeight: 44, alignment: .leading)
        } else {
            ClaimValueView(value: field.item.value, path: field.item.path,
                textFont: prominent ? .title3.weight(.semibold) : .body).textSelection(.enabled)
        }
        if let description = field.description { Text(description).font(.callout) }
    }.fixedSize(horizontal: false, vertical: true)
}

private struct PaymentPayee: View {
    let fields: [PaymentDisplayField]
    var detailsInitiallyExpanded = false
    @State private var allowedRemoteSource: String?
    @State private var loadedLogo: (request: LogoRequest, image: UIImage)?

    private struct LogoRequest: Equatable { let source: String?; let allowRemote: Bool }

    var body: some View {
        let logo = fields.first(where: \.isLogo)
        let source = logo?.item.rawValue
        let allowRemote = source != nil && source == allowedRemoteSource
        let request = LogoRequest(source: source, allowRemote: allowRemote)
        let remote = source.flatMap(paymentWebsite) != nil
        let details = fields.filter { !$0.isLogo && $0.placement == .details }
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(fields.filter { !$0.isLogo && $0.placement != .details }) {
                        fieldView($0, prominent: $0.placement == .prominent)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
                if let logo, let loadedLogo, loadedLogo.request == request {
                    PaymentLogoView(image: loadedLogo.image, label: logo.item.label, description: logo.description)
                }
            }
            if !details.isEmpty || (remote && !allowRemote) {
                MetadataDisclosure(title: String(localized: "Payee details", bundle: .module),
                    initiallyExpanded: detailsInitiallyExpanded, accessibilityIdentifier: "payment-payee-details-toggle") {
                    ForEach(details) { fieldView($0, prominent: false) }
                    if remote && !allowRemote {
                        Button(String(localized: "Load payee logo", bundle: .module)) { allowedRemoteSource = source }
                            .frame(minHeight: 44)
                    }
                }
            }
        }.padding().frame(maxWidth: .infinity, alignment: .leading)
            .background(.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
            .task(id: request) {
                // The payee remains mounted when no image is available; an empty logo view does not.
                loadedLogo = nil
                guard let source = request.source,
                      let image = await loadPaymentLogo(source, allowRemote: request.allowRemote),
                      !Task.isCancelled else { return }
                loadedLogo = (request, image)
            }
    }
}
