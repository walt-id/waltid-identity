package id.walt.webwallet.service.exchange.transactiondata

import id.walt.verifier.openid.transactiondata.DecodedTransactionData
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfile

object PaymentAuthorizationTransactionDataProfile : TransactionDataTypeProfile(
    type = "org.waltid.transaction-data.payment-authorization",
    displayName = "Payment Authorization",
) {
    override fun validate(decoded: DecodedTransactionData) {
        val details = decoded.details
        require("amount" in details) { "payment transaction_data requires 'amount'" }
        require("currency" in details) { "payment transaction_data requires 'currency'" }
        require("payee" in details) { "payment transaction_data requires 'payee'" }
    }
}
