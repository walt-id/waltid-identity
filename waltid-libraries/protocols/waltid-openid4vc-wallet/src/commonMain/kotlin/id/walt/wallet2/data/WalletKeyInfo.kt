package id.walt.wallet2.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Lightweight metadata about a key; does not expose private key material. */
@Serializable
data class WalletKeyInfo(
    val keyId: String,
    val keyType: String,
    val algorithm: String? = null,
    val publicJwk: JsonObject? = null,
)
