package id.walt.openid4vci.preauthorized

import id.walt.openid4vci.responses.token.TokenGrantFailure

/** Pre-authorized grant outcomes available to the service hosting the token endpoint. */
sealed interface PreAuthorizedCodeFailure : TokenGrantFailure {
    data object WrongTxCode : PreAuthorizedCodeFailure

    /** The persisted grant is permanently locked. The ID comes from the stored grant. */
    data class Locked(val issuanceSessionId: String?) : PreAuthorizedCodeFailure
}
