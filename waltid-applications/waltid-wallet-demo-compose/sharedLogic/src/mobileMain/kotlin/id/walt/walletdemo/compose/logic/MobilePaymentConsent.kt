package id.walt.walletdemo.compose.logic

import id.walt.wallet2.consent.PaymentFieldPlacement
import id.walt.wallet2.consent.PreparedPaymentConsent

fun PreparedPaymentConsent.toDemoPaymentConsent() = WalletDemoPaymentConsent(
    revision, payment.locale, payment.title, payment.securityHint, payment.affirmativeAction, payment.denialAction,
    requiresUnsignedRequestWarning,
    payment.fields.map { field -> WalletDemoPaymentField(field.label, field.descriptionText, field.value, when (field.placement) {
        PaymentFieldPlacement.PROMINENT -> WalletDemoPaymentFieldPlacement.Prominent
        PaymentFieldPlacement.MAIN -> WalletDemoPaymentFieldPlacement.Main
        PaymentFieldPlacement.DETAILS -> WalletDemoPaymentFieldPlacement.Details
        PaymentFieldPlacement.OMITTED -> WalletDemoPaymentFieldPlacement.Omitted
    }) },
)
