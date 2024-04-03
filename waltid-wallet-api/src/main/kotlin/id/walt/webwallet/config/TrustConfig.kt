package id.walt.webwallet.config

data class TrustConfig(
    val issuersRecord: TrustRecord,
    val verifiersRecord: TrustRecord
) : WalletConfig {
    data class TrustRecord(
        val baseUrl: String,
        val trustRecordPath: String,
        val governanceRecordPath: String,
    )
}