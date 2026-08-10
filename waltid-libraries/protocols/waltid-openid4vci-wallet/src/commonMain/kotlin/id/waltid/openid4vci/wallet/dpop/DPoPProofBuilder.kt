package id.waltid.openid4vci.wallet.dpop

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.toPublicJwk
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Builds RFC 9449 DPoP proofs without exposing access-token or private-key material. */
class DPoPProofBuilder {

    /**
     * Creates a unique proof for one HTTP request using a crypto2 signing key.
     *
     * [accessToken] is required for protected-resource requests and omitted for token requests.
     */
    suspend fun buildProof(
        key: Crypto2Key,
        httpMethod: String,
        targetUri: String,
        accessToken: String? = null,
        nonce: String? = null,
        supportedAlgorithms: Set<String>? = null,
    ): String {
        require(httpMethod.isNotBlank()) { "DPoP HTTP method cannot be blank" }
        val algorithm = key.selectJwsAlgorithm(supportedAlgorithms)
        val htu = normalizedTargetUri(targetUri)

        val header = buildJsonObject {
            put("typ", "dpop+jwt")
            put("jwk", key.exportPublicJwk().toJsonObject())
        }
        val payload = buildJsonObject {
            put("jti", Uuid.random().toString())
            put("htm", httpMethod.uppercase())
            put("htu", htu)
            put("iat", Clock.System.now().toEpochMilliseconds() / 1_000)
            accessToken?.let { put("ath", SHA256().digest(it.encodeToByteArray()).encodeToBase64Url()) }
            nonce?.let { put("nonce", it) }
        }
        return CompactJws.sign(
            payload = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(),
            key = key,
            algorithm = algorithm,
            protectedHeader = header,
        )
    }

    internal fun normalizedTargetUri(value: String): String {
        val url = Url(value)
        require(url.host.isNotBlank()) { "DPoP target URI must include a host" }

        val normalizedHost = when {
            url.host.startsWith("[") -> url.host
            ':' in url.host -> "[${url.host}]"
            else -> url.host
        }
        val nonDefaultPort = url.specifiedPort.takeIf { port ->
            port != 0 && port != url.protocol.defaultPort
        }

        return buildString {
            append(url.protocol.name)
            append("://")
            append(normalizedHost)
            nonDefaultPort?.let { port ->
                append(':')
                append(port)
            }
            append(url.encodedPath.ifEmpty { "/" })
        }
    }
}

private suspend fun Crypto2Key.exportPublicJwk(): EncodedKey.Jwk {
    val exported = capabilities.publicKeyExporter?.exportPublicKey()
        ?: throw IllegalArgumentException("DPoP signing key does not support public-key export")
    return exported.toPublicJwk(spec)
}

private fun EncodedKey.Jwk.toJsonObject(): JsonObject =
    Json.parseToJsonElement(data.toByteArray().decodeToString()) as? JsonObject
        ?: throw IllegalArgumentException("Exported JWK must be a JSON object")
