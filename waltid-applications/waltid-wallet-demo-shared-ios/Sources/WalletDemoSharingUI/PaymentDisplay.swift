import Foundation
import WalletSDK

/// One rendering contract for issuer-instructed and legacy payments. It grants no consent.
struct PaymentDisplay: Identifiable {
    let id: String
    let title: String?
    let securityHint: String?
    let requiresUnsignedRequestWarning: Bool
    let fields: [PaymentDisplayField]
    var technical: [ClaimItem] = []
}

struct PaymentDisplayField: Identifiable {
    let item: ClaimItem
    let placement: PaymentConsentFieldPlacement
    var description: String? = nil
    var id: String { item.id }
}

// Keep these rendering groups aligned with the Compose demo; validated values, labels,
// locale and placement continue to come from the same wallet core.
enum PaymentDisplayGroup { case payee, amount, timing, provider, other }

extension PaymentDisplayField {
    var paymentPath: [String] {
        guard item.pathComponents.dropFirst().first == "details" else { return item.pathComponents }
        let fields = Array(item.pathComponents.dropFirst(2))
        return fields.first == "payload" ? Array(fields.dropFirst()) : fields
    }
    var group: PaymentDisplayGroup {
        switch paymentPath.first {
        case "payee": .payee
        case "amount", "currency": .amount
        case "execution_date", "recurrence": .timing
        case "pisp": .provider
        default: .other
        }
    }
    var isLogo: Bool { paymentPath == ["payee", "logo"] }
    var isWebsite: Bool { paymentPath == ["payee", "website"] }
}

extension PaymentReviewState {
    func displays(transactions: [ClaimGroup]) -> [PaymentDisplay] {
        var displays: [PaymentDisplay] = []
        if let consent {
            displays.append(PaymentDisplay(id: consent.revision, title: consent.title, securityHint: consent.securityHint,
                requiresUnsignedRequestWarning: consent.requiresUnsignedRequestWarning,
                fields: consent.fields.enumerated().map { index, field in
                    PaymentDisplayField(item: ClaimItem(path: ClaimItemPath(id: "payment").indexedChild(index),
                        pathComponents: field.path, label: field.label, value: field.displayValue, rawValue: field.value),
                        placement: field.placement, description: field.description)
                }))
        }
        // An unresolved/rejected TS-12 consent must never fall back to transaction values.
        for transaction in transactions where self == .notRequired || transaction.transactionType != "urn:eudi:sca:payment:1" {
            displays.append(PaymentDisplay(id: transaction.id, title: nil, securityHint: nil, requiresUnsignedRequestWarning: false,
                fields: transaction.items.filter { $0.pathComponents.dropFirst().first == "details" }.map {
                    PaymentDisplayField(item: $0, placement: $0.isPaymentSummary ? .prominent : .main)
                }, technical: transaction.items.filter { $0.pathComponents.dropFirst().first != "details" }))
        }
        return displays
    }
}

private let paymentTimestampPaths: Set<[String]> = [
    ["date_time"], ["execution_date"], ["recurrence", "start_date"], ["recurrence", "end_date"],
]

private extension PaymentConsentField {
    var displayValue: DisplayValue {
        guard paymentTimestampPaths.contains(path), let instant = WalletInstant.parseTimestamp(value) else {
            return .text(value)
        }
        return .instant(instant)
    }
}

extension SharingRequest {
    public var hasPaymentTransaction: Bool { transactionData.contains(where: \.isPaymentTransaction) }
}

extension ClaimGroup {
    var isPaymentTransaction: Bool {
        transactionType == "urn:eudi:sca:payment:1" || transactionType == "org.waltid.transaction-data.payment-authorization"
    }
}

private extension ClaimItem {
    var isPaymentSummary: Bool {
        let fields = Array(pathComponents.dropFirst(2))
        let path = fields.first == "payload" ? Array(fields.dropFirst()) : fields
        return pathComponents.dropFirst().first == "details" &&
            [["amount"], ["currency"], ["payee"], ["payee", "name"]].contains(path)
    }
}
