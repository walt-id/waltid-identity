package id.waltid.openid4vci.wallet.dpop

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import io.ktor.http.Url
import io.ktor.http.hostWithPort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Builds RFC 9449 DPoP proofs without exposing access-token or private-key material. */
class DPoPProofBuilder {

    /**
     * Creates a unique proof for one HTTP request.
     *
     * [accessToken] is required for protected-resource requests and omitted for token requests.
     */
    suspend fun buildProof(
        key: Key,
        httpMethod: String,
        targetUri: String,
        accessToken: String? = null,
        nonce: String? = null,
        supportedAlgorithms: Set<String>? = null,
    ): String {
        require(httpMethod.isNotBlank()) { "DPoP HTTP method cannot be blank" }
        val htu = normalizedTargetUri(targetUri)
        val algorithm = key.keyType.jwsAlg
        require(supportedAlgorithms.isNullOrEmpty() || algorithm in supportedAlgorithms) {
            "Selected holder key algorithm '$algorithm' is not supported for DPoP"
        }

        val publicJwk = Json.parseToJsonElement(key.getPublicKey().exportJWK()).jsonObject
        val header = buildJsonObject {
            put("typ", "dpop+jwt")
            put("alg", algorithm)
            put("jwk", publicJwk)
        }
        val payload = buildJsonObject {
            put("jti", Uuid.random().toString())
            put("htm", httpMethod.uppercase())
            put("htu", htu)
            put("iat", Clock.System.now().toEpochMilliseconds() / 1_000)
            accessToken?.let { put("ath", SHA256().digest(it.encodeToByteArray()).encodeToBase64Url()) }
            nonce?.let { put("nonce", it) }
        }
        return key.signJws(payload.toString().encodeToByteArray(), header.toJsonElement().jsonObject)
    }

    internal fun normalizedTargetUri(value: String): String {
        val url = Url(value)
        require(url.host.isNotBlank()) { "DPoP target URI must include a host" }
        return buildString {
            append(url.protocol.name)
            append("://")
            append(url.hostWithPort)
            append(url.encodedPath.ifEmpty { "/" })
        }
    }
}
