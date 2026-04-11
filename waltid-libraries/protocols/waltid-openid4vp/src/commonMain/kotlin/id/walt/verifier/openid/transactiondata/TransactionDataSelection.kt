package id.walt.verifier.openid.transactiondata

fun filterTransactionDataForCredentialId(
    transactionData: List<String>?,
    credentialId: String,
): List<String> = decodeList(transactionData.orEmpty())
    .filter { credentialId in it.transactionData.credentialIds }
    .map { it.encoded }
