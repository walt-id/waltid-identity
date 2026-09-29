package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.WalletKeyStoreEntry
import kotlinx.serialization.json.*

internal suspend fun batchTestCredential(publicJwk: JsonObject, vct: String = "identity"): String {
    val issuerKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId("test-issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN)),
    )
    return CompactJws.sign(
        key = issuerKey,
        algorithm = JwsAlgorithm.ES256,
        payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", vct)
            put("iat", 1700000000)
            put("given_name", "Ada")
            putJsonObject("cnf") { put("jwk", publicJwk) }
        }.toString().encodeToByteArray(),
        protectedHeader = buildJsonObject { put("typ", "dc+sd-jwt") },
    ) + "~"
}

internal suspend fun batchTestCredential(key: Key): String = batchTestCredential(key.getPublicKey().exportJWKObject())

internal suspend fun batchTestCredential(key: WalletKeyStoreEntry): String = batchTestCredential(
    key.crypto2Key?.let {
        val encoded = it.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(it.spec)
        Json.parseToJsonElement(encoded.data.toByteArray().decodeToString()).jsonObject
    } ?: key.legacyKey!!.getPublicKey().exportJWKObject()
)

internal suspend fun batchTestResponse(proofs: List<String>, vct: String = "identity"): String = buildJsonObject {
    put("credentials", JsonArray(proofs.map {
        val jwk = CompactJws.decodeUnverified(it).protectedHeader["jwk"]!!.jsonObject
        buildJsonObject { put("credential", batchTestCredential(jwk, vct)) }
    }))
}.toString()
