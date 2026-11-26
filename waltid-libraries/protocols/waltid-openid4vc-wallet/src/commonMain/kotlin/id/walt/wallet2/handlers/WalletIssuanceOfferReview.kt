package id.walt.wallet2.handlers

/** A retained offer and the authorization server selected during discovery. */
class WalletIssuanceOfferReview internal constructor(
    val session: WalletIssuanceBatchSession,
    val authorizationServer: String,
    val clientIdentification: IssuanceClientIdentification,
)

/** Whether the selected server and configured authentication allow a pre-authorized request without a client ID. */
enum class IssuanceClientIdentification {
    ANONYMOUS_ALLOWED,
    CLIENT_ID_REQUIRED,
}
