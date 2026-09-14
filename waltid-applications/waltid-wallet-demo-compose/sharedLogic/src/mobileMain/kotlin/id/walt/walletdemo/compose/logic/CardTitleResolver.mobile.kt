package id.walt.walletdemo.compose.logic

import id.walt.credentials.display.CredentialTitles

internal actual fun resolveCardTitle(
    format: String,
    credentialDataJson: String?,
    displayName: String?,
    fallback: String,
): String = CredentialTitles.fromPayload(
    format = format,
    credentialDataJson = credentialDataJson,
    displayName = displayName,
    fallback = fallback,
)
