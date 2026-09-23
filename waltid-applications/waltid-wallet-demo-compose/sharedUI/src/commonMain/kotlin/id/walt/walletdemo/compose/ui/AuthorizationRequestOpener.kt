package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

/**
 * Opens the issuer authorization URL for authorization-code issuance.
 *
 * Web navigates the current tab so the issuer redirect returns to this same page.
 * Mobile uses the platform URI handler (external browser / associated app).
 */
@Composable
internal expect fun rememberAuthorizationRequestOpener(): (String) -> Unit
