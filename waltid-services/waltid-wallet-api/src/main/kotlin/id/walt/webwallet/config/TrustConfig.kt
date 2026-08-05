package id.walt.webwallet.config

import kotlinx.serialization.Serializable

@Serializable
data class TrustConfig(
    val issuersRecord: TrustRecord,
    val verifiersRecord: TrustRecord,
    /** Trusted public keys used to verify signed Credential Issuer Metadata. */
    val issuerMetadataSigners: Map<String, List<IssuerMetadataSignerConfig>> = emptyMap(),
) : WalletConfig() {
    @Serializable
    data class TrustRecord(
        val baseUrl: String,
        val trustRecordPath: String,
        val governanceRecordPath: String,
    )

    @Serializable
    data class IssuerMetadataSignerConfig(
        /** Public JWK used for verification; private key material is not accepted. */
        val publicJwk: String,
        /** Optional JWS `kid` value bound to this configured key. */
        val keyId: String? = null,
        /** Optional JWS `alg` value bound to this configured key. */
        val algorithm: String? = null,
    )
}
