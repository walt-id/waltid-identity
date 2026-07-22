package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface WalletAttestationProvider {
    suspend fun getAttestationJwt(instancePublicKeyJwk: EncodedKey.Jwk, clientId: String): String {
        val publicJwk = instancePublicKeyJwk.requirePublicJwk()
        val legacyPublicKey = JWKKey.importJWK(Json.encodeToString(publicJwk)).getOrThrow()
        return getAttestationJwt(legacyPublicKey, clientId)
    }

    @Deprecated("Use the EncodedKey.Jwk overload")
    suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String
}

internal fun EncodedKey.Jwk.requirePublicJwk(): JsonObject {
    require(!privateMaterial) { "Wallet attestation JWK must not contain private material" }
    return Jwk.parse(this).also {
        require(!Jwk.containsPrivateMaterial(it)) { "Wallet attestation JWK must not contain private material" }
    }
}

internal suspend fun Key.exportPublicCrypto2Jwk(): EncodedKey.Jwk {
    val publicJwk = getPublicKey().exportJWKObject()
    return EncodedKey.Jwk(
        data = BinaryData(Json.encodeToString(publicJwk).encodeToByteArray()),
        privateMaterial = false,
    )
}
