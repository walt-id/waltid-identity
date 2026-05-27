package id.walt.wallet2.data

import kotlinx.serialization.Serializable

/** A DID entry stored in a wallet's DID store. */
@Serializable
data class WalletDidEntry(
    val did: String,
    /** The raw JSON-serialized DID document. */
    val document: String,
)
