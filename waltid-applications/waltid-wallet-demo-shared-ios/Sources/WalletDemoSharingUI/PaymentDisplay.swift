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

extension PaymentReviewState {
    func displays(transactions: [ClaimGroup]) -> [PaymentDisplay] {
        var displays: [PaymentDisplay] = []
        if let consent {
            displays.append(PaymentDisplay(id: consent.revision, title: consent.title, securityHint: consent.securityHint,
                requiresUnsignedRequestWarning: consent.requiresUnsignedRequestWarning,
                fields: consent.fields.enumerated().map { index, field in
                    PaymentDisplayField(item: ClaimItem(path: ClaimItemPath(id: "payment").indexedChild(index),
                        label: field.label, value: .text(field.value), rawValue: field.value),
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
