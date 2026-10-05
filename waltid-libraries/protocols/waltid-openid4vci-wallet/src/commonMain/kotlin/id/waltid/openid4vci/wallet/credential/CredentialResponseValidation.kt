package id.waltid.openid4vci.wallet.credential

import id.walt.openid4vci.responses.credential.CredentialResponse

/** Validates the wire response shared by initial and deferred issuance (OpenID4VCI §§8.3, 9.2). */
fun CredentialResponse.validateCredentialResponse(
    status: Int,
    expectedTransactionId: String? = null,
): CredentialResponse = apply {
    when (status) {
        200 -> {
            require(!credentials.isNullOrEmpty() && transactionId == null && interval == null) {
                "An issued response must contain credentials without deferred parameters"
            }
        }
        202 -> {
            require(credentials == null && !transactionId.isNullOrBlank() && notificationId == null) {
                "A deferred response must contain a transaction identifier without credentials or notification"
            }
            require(interval?.let { it > 0 } == true) { "A deferred response requires a positive polling interval" }
            require(expectedTransactionId == null || transactionId == expectedTransactionId) {
                "Deferred response changed the transaction identifier"
            }
        }
        else -> error("Unexpected credential response status: $status")
    }
}
