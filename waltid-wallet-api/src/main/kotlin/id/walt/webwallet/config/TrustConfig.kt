package id.walt.webwallet.config

data class TrustConfig(
    val entra: TrustEntry? = null
) : WalletConfig {
    data class TrustEntry(
        val issuer: TrustItem,
        val verifier: TrustItem,
    ) {
        data class TrustItem(
            val baseUrl: String,
            val trustRecordPath: String,
        )
    }
}