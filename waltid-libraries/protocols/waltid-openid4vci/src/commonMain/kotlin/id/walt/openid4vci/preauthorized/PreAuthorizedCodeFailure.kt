package id.walt.openid4vci.preauthorized

/** Pre-authorized grant outcomes available to the service hosting the token endpoint. */
sealed interface PreAuthorizedCodeFailure {
    data object WrongTxCode : PreAuthorizedCodeFailure

    /** The persisted grant is permanently locked. The ID comes from the stored grant. */
    data class Locked(val issuanceSessionId: String?) : PreAuthorizedCodeFailure
}
