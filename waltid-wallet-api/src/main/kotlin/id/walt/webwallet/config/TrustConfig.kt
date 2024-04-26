package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class TrustConfig(
    val issuersRecord: TrustRecord,
    val verifiersRecord: TrustRecord
) : WalletConfig() {
    @Serializable
    data class TrustRecord(
        val baseUrl: String,
        val trustRecordPath: String,
        val governanceRecordPath: String,
    )
}