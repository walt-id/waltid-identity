package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_loading
import id.walt.walletdemo.compose.ui.resources.credential_status_checked
import id.walt.walletdemo.compose.ui.resources.credential_status_clear
import id.walt.walletdemo.compose.ui.resources.credential_status_clear_title
import id.walt.walletdemo.compose.ui.resources.credential_status_invalid
import id.walt.walletdemo.compose.ui.resources.credential_status_last_known_invalid
import id.walt.walletdemo.compose.ui.resources.credential_status_last_known_revoked
import id.walt.walletdemo.compose.ui.resources.credential_status_last_known_suspended
import id.walt.walletdemo.compose.ui.resources.credential_status_not_checked
import id.walt.walletdemo.compose.ui.resources.credential_status_not_provided
import id.walt.walletdemo.compose.ui.resources.credential_status_not_suspended
import id.walt.walletdemo.compose.ui.resources.credential_status_partial
import id.walt.walletdemo.compose.ui.resources.credential_status_purpose_other
import id.walt.walletdemo.compose.ui.resources.credential_status_purpose_refresh
import id.walt.walletdemo.compose.ui.resources.credential_status_purpose_revocation
import id.walt.walletdemo.compose.ui.resources.credential_status_purpose_suspension
import id.walt.walletdemo.compose.ui.resources.credential_status_purpose_token
import id.walt.walletdemo.compose.ui.resources.credential_status_purpose_message
import id.walt.walletdemo.compose.ui.resources.credential_status_review
import id.walt.walletdemo.compose.ui.resources.credential_status_revoked
import id.walt.walletdemo.compose.ui.resources.credential_status_stale
import id.walt.walletdemo.compose.ui.resources.credential_status_suspended
import id.walt.walletdemo.compose.ui.resources.credential_status_unavailable
import id.walt.walletdemo.compose.ui.resources.credential_status_unsupported
import id.walt.walletdemo.compose.ui.resources.credential_status_unverified
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WalletDemoCredentialStatus.title(): String = stringResource(when {
    positive -> Res.string.credential_status_clear_title
    lastKnown && kind == "REVOKED" -> Res.string.credential_status_last_known_revoked
    lastKnown && kind == "INVALID" -> Res.string.credential_status_last_known_invalid
    lastKnown && kind == "SUSPENDED" -> Res.string.credential_status_last_known_suspended
    else -> when (kind) {
    "LOADING" -> Res.string.credential_status_loading
    "NOT_PROVIDED" -> Res.string.credential_status_not_provided
    "NOT_CHECKED" -> Res.string.credential_status_not_checked
    "NOT_REVOKED" -> Res.string.credential_status_clear
    "NOT_SUSPENDED" -> Res.string.credential_status_not_suspended
    "CHECKED" -> Res.string.credential_status_checked
    "REVOKED" -> Res.string.credential_status_revoked
    "INVALID" -> Res.string.credential_status_invalid
    "SUSPENDED" -> Res.string.credential_status_suspended
    "OUT_OF_DATE" -> Res.string.credential_status_stale
    "UNVERIFIED" -> Res.string.credential_status_unverified
    "UNSUPPORTED" -> Res.string.credential_status_unsupported
    "PARTIAL" -> Res.string.credential_status_partial
    "NEEDS_REVIEW" -> Res.string.credential_status_review
    "SERVER_UNSUPPORTED" -> Res.string.credential_status_unavailable
    else -> Res.string.credential_status_unavailable
    }
})

@Composable
internal fun statusPurposeTitle(purpose: String): String = stringResource(when (purpose) {
    "revocation" -> Res.string.credential_status_purpose_revocation
    "suspension" -> Res.string.credential_status_purpose_suspension
    "refresh" -> Res.string.credential_status_purpose_refresh
    "token" -> Res.string.credential_status_purpose_token
    "message" -> Res.string.credential_status_purpose_message
    else -> Res.string.credential_status_purpose_other
})
