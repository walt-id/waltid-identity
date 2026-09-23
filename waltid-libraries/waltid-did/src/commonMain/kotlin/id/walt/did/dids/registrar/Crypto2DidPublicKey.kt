package id.walt.did.dids.registrar

import id.walt.crypto.keys.PublicKeyIds
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.publicOnly
import id.walt.crypto2.keys.toPublicJwk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val privateJwkMembers = setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k")

internal suspend fun Key.exportPublicJwkObject(): JsonObject {
    val exported = capabilities.publicKeyExporter?.exportPublicKey()
        ?: throw IllegalArgumentException("Crypto2 key ${id.value} does not support public-key export")
    val publicJwk = when (exported) {
        is EncodedKey.Jwk -> exported.publicOnly()
        is EncodedKey.SpkiDer -> exported.toPublicJwk(spec)
        is EncodedKey.Pkcs8Der -> throw IllegalArgumentException("Public-key exporter returned private PKCS8 material")
    }
    val json = Json.parseToJsonElement(publicJwk.data.toByteArray().decodeToString()).jsonObject
    require(privateJwkMembers.none(json::containsKey)) { "Public-key export contains private JWK material" }
    val kid = json["kid"]?.jsonPrimitive?.contentOrNull
    // Vault/HTTP kids are ops locators — never embed them in DID documents.
    return if (kid != null && PublicKeyIds.isHttpKeyId(kid)) {
        JsonObject(json.toMutableMap().apply { remove("kid") })
    } else {
        json
    }
}
