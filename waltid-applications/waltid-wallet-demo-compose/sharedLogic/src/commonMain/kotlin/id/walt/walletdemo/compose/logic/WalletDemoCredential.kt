package id.walt.walletdemo.compose.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject


@Serializable
data class WalletDemoCredential(
    val id: String,
    val format: String,
    val issuer: String,
    val subject: String,
    val label: String,
    val addedAt: String,
    val credentialData : JsonObject ,
)
