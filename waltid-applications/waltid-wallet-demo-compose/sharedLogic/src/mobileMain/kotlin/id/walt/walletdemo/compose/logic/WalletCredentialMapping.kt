package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.credentials.WalletCredential

/** One display mapping for in-app and provider result receipts. */
fun WalletCredential.toDemoCredential(): WalletDemoCredential = WalletDemoCredential(
    id = id, format = format, issuer = issuer, subject = subject, label = label ?: format,
    addedAt = addedAt, credentialDataJson = credentialDataJson, metadataJson = metadataJson,
)
