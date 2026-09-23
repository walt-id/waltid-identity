package id.walt.walletdemo.compose.logic

internal actual fun resolveCardTitle(
    format: String,
    credentialDataJson: String?,
    displayName: String?,
    fallback: String,
): String = displayName?.trim()?.takeIf { it.isNotBlank() } ?: fallback
