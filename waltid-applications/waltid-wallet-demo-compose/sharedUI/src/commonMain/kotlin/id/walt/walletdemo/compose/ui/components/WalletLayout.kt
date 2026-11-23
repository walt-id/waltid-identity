package id.walt.walletdemo.compose.ui.components

import androidx.compose.ui.unit.dp

/** Shared reading-column limit; compact hosts keep their available width. */
internal val WalletContentMaxWidth = 640.dp

/** Keeps the card, issuer and facts aligned within a readable column. */
internal val WalletCredentialContentMaxWidth = CredentialCardMaxWidth + 40.dp
