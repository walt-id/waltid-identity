package id.waltid.openid4vp.wallet

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.json.Json

internal object WalletCrypto2KeyAdapter {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    suspend fun signingKey(key: Key) =
        (key as? JWKKey)?.takeUnless { it.keyType == KeyType.secp256k1 }?.let {
            val jwk = it.exportJWKObject()
            if (!Jwk.containsPrivateMaterial(jwk)) return@let null
            val encoded = EncodedKey.Jwk(
                BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
                privateMaterial = true,
            )
            val keyId = runCatching { it.getKeyId() }.getOrNull()
                ?: Jwk.sha256Thumbprint(encoded)
            runtime.restore(
                encoded.toStoredSoftwareKey(KeyId(keyId), setOf(KeyUsage.SIGN))
            )
        }
}
