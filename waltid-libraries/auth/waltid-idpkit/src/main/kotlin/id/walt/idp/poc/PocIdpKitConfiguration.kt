package id.walt.idp.poc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PocIdpKitConfiguration(
    /**
     * key as JWK
     */
    val key: String, // TODO: switch to serialized waltid-crypto keys
    /**
     * request to walt.id verifier request
     */
    val verifierRequest: JsonObject,
    /**
     * issuer URL
     */
    val issuer: String,
    val redirectUrl: String,
    val walletUrl: String,
    val enableDebug: Boolean,
    val claimMapping: Map<String, String>,
)
