package id.walt.openid4vci.preauthorized

/** Known `invalid_grant` descriptions for pre-authorized code redemption failures. */
object PreAuthorizedCodeErrorDescriptions {
    const val WRONG_TX_CODE = "tx_code is invalid"
    const val TX_CODE_ATTEMPTS_EXHAUSTED = "Too many incorrect tx_code attempts"
    const val INVALID_CODE = "Pre-authorized code is invalid or has already been used"
}
