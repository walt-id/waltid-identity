package id.walt.walletdemo.compose.logic

internal expect fun resolveCardTitle(
    format: String,
    credentialDataJson: String?,
    displayName: String?,
    fallback: String,
): String
