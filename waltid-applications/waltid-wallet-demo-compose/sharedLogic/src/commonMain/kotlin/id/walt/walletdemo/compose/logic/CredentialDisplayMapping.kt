package id.walt.walletdemo.compose.logic

fun CredentialSummary.toCredentialDetails(): CredentialDetails =
    CredentialDisplayNormalizer.toDetails(this)
