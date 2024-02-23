package id.walt.crypto.keys

data class OCIKeyConfig(
    val tenancyOcid: String,
    val userOcid: String,
    val fingerprint: String,
    val managementEndpoint: String,
    val keyId: String,
    val OCIDKeyID: String,
    val cryptoEndpoint: String
)