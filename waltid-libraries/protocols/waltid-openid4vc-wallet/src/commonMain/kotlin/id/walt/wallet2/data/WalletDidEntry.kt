package id.walt.wallet2.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A DID entry stored in a wallet's DID store. */
@Serializable
data class WalletDidEntry(
    val did: String,
    /** The DID document as a parsed JSON object. */
    val document: JsonObject,
)
