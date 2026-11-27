package id.walt.wallet2.handlers

/** Another owner holds the issuance claim, or its remote outcome could not be recovered. */
class IssuanceOutcomeUncertainException : IllegalStateException("The issuance outcome is pending. Check its status before continuing.")

/** A retained issuance record could not be read to establish ownership. */
class IssuanceSessionStorageException(cause: Throwable) : IllegalStateException("Issuance session storage is unavailable.", cause)

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
