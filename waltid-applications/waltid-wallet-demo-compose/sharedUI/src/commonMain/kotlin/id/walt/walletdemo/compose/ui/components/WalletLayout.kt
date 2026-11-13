package id.walt.walletdemo.compose.ui.components

import androidx.compose.ui.unit.dp

/** Shared reading-column limit; compact hosts keep their available width. */
internal val WalletContentMaxWidth = 640.dp

/** Card, issuer and facts stay aligned; a readable badge must also clear the fixed stack peek. */
internal val WalletCredentialContentMaxWidth = CredentialCardMaxWidth + 40.dp
