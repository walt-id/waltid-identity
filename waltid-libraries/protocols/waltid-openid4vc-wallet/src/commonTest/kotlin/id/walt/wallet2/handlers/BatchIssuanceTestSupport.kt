package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.keys.toPublicJwk
import id.walt.wallet2.data.WalletKeyStoreEntry
import kotlinx.serialization.json.*

internal suspend fun batchTestCredential(publicJwk: JsonObject): String =
    JWKKey.generate(KeyType.secp256r1).signJws(
        buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", "identity")
            put("iat", 1700000000)
            put("given_name", "Ada")
            putJsonObject("cnf") { put("jwk", publicJwk) }
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("dc+sd-jwt")),
    ) + "~"

internal suspend fun batchTestCredential(key: Key): String = batchTestCredential(key.getPublicKey().exportJWKObject())

internal suspend fun batchTestCredential(key: WalletKeyStoreEntry): String = batchTestCredential(
    key.crypto2Key?.let {
        val encoded = it.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(it.spec)
        Json.parseToJsonElement(encoded.data.toByteArray().decodeToString()).jsonObject
    } ?: key.legacyKey!!.getPublicKey().exportJWKObject()
)

internal suspend fun batchTestResponse(proofs: List<String>): String = buildJsonObject {
    put("credentials", JsonArray(proofs.map {
        val jwk = CompactJws.decodeUnverified(it).protectedHeader["jwk"]!!.jsonObject
        buildJsonObject { put("credential", batchTestCredential(jwk)) }
    }))
}.toString()
