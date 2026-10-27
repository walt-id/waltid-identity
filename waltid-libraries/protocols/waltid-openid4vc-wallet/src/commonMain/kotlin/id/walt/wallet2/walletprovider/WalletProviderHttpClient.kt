package id.walt.wallet2.walletprovider

import id.walt.crypto.keys.Key as LegacyKey
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationRequest
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The hosted Enterprise Stack demo. The wallet-provider feature is not deployed on this instance,
 * and [SERVICE_PATH] is not provisioned. Calls fail until that deployment is updated.
 */
object EnterpriseDemoWalletProvider {
    const val BASE_URL = "https://org4.enterprise.demo.walt.id"
    const val SERVICE_PATH = "org4.demo.wallet-provider"
}

/**
 * Calls one Enterprise Wallet Provider over HTTP for a wallet instance attestation and a key attestation.
 *
 * The unit binding is kept for this client instance. A later receipt of a new holder key refreshes that
 * unit instead of presenting another key's attestation.
 */
private val log = KotlinLogging.logger {}

class WalletProviderHttpClient(
    private val baseUrl: String,
    private val servicePath: String,
    private val bearerToken: String = "",
    private val hostHeader: String = "",
    private val httpClient: HttpClient,
    private val verificationKeyFromJwt: suspend (String) -> Key,
) : WalletAttestationProvider, KeyAttestationProvider {
    private val mutex = Mutex()
    private var binding: Binding? = null
    private var learnedVerificationKey: Key? = null

    override val verificationKey: Key
        get() = learnedVerificationKey ?: error("Wallet provider has not returned a signed attestation yet")

    override suspend fun getAttestationJwt(instancePublicKeyJwk: EncodedKey.Jwk, clientId: String): String =
        mutex.withLock {
            val instanceJwk = publicJwk(instancePublicKeyJwk)
            val thumbprint = Jwk.sha256Thumbprint(instancePublicKeyJwk)
            val current = binding
            val jwt = when {
                current == null -> {
                    val created = post("units", activateBody(instanceJwk, listOf(instanceJwk), clientId))
                    binding = Binding(created.unitId, thumbprint, created.attestedKeys.ifEmpty { listOf(instanceJwk) })
                    created.wia
                }
                current.instanceThumbprint != thumbprint -> {
                    val refreshed = post(
                        "units/${current.unitId}/refresh",
                        buildJsonObject {
                            put("rotate", "wia")
                            put("instance_jwk", instanceJwk)
                        },
                    )
                    binding = current.copy(instanceThumbprint = thumbprint)
                    refreshed.wia
                }
                else -> post("units/${current.unitId}/wua", buildJsonObject { put("need", "wia") }).wia
            }
            jwt ?: error("Wallet provider did not return a wallet instance attestation")
        }

    @Deprecated("Use the EncodedKey.Jwk overload")
    override suspend fun getAttestationJwt(instanceKey: LegacyKey, clientId: String): String {
        val publicJwk = instanceKey.getPublicKey().exportJWKObject()
        val encoded = EncodedKey.Jwk(BinaryData(Json.encodeToString(publicJwk).encodeToByteArray()), false)
        return getAttestationJwt(encoded, clientId)
    }

    override suspend fun attest(request: KeyAttestationRequest): String = mutex.withLock {
        val proofJwk = Jwk.parse(request.proofKey)
        val proofThumbprint = Jwk.sha256Thumbprint(request.proofKey)
        val current = binding ?: run {
            val created = post("units", activateBody(proofJwk, listOf(proofJwk), request.credentialIssuer))
            Binding(created.unitId, proofThumbprint, listOf(proofJwk)).also { binding = it }
        }
        val known = current.attestedKeys.map { Jwk.sha256Thumbprint(EncodedKey.Jwk(BinaryData(Json.encodeToString(it).encodeToByteArray()), false)) }
        val response = if (proofThumbprint !in known) {
            val combined = distinctKeys(current.attestedKeys + proofJwk).takeLast(MAX_ATTESTED_KEYS)
            post(
                "units/${current.unitId}/refresh",
                buildJsonObject {
                    put("rotate", "ka")
                    put("attested_keys", JsonArray(combined))
                    put("sign_attested_keys", buildJsonArray { add(proofJwk) })
                    request.nonce?.let { put("nonce", it) }
                    put("credential_issuer", request.credentialIssuer)
                },
            ).also { binding = current.copy(attestedKeys = combined) }
        } else {
            post(
                "units/${current.unitId}/wua",
                buildJsonObject {
                    put("need", "ka")
                    request.nonce?.let { put("nonce", it) }
                    put("credential_issuer", request.credentialIssuer)
                    put("attested_keys", buildJsonArray { add(proofJwk) })
                },
            )
        }
        val jwt = response.ka ?: error("Wallet provider did not return a key attestation")
        learnedVerificationKey = verificationKeyFromJwt(jwt)
        jwt
    }

    private suspend fun activateBody(instanceJwk: JsonObject, attestedKeys: List<JsonObject>, clientId: String) = buildJsonObject {
        put("instance_jwk", instanceJwk)
        put("attested_keys", JsonArray(attestedKeys))
        put("client_id", clientId)
        put("composition", "wia_ka")
        put("mint", "wia")
        put("sign_tokens", true)
    }

    private suspend fun post(path: String, body: JsonObject): ProviderTokens {
        val response = httpClient.post(endpoint(path)) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            authenticate()
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Wallet provider ${endpoint(path)} returned HTTP ${response.status.value}: $text")
        }
        val json = Json.parseToJsonElement(text).jsonObject
        rememberVerificationKey(json["wia"]?.jsonPrimitive?.contentOrNull)
        rememberVerificationKey(json["ka"]?.jsonPrimitive?.contentOrNull)
        return ProviderTokens(
            unitId = json["unit_id"]?.jsonPrimitive?.content ?: binding?.unitId ?: error("Wallet provider response has no unit_id"),
            wia = json["wia"]?.jsonPrimitive?.contentOrNull,
            ka = json["ka"]?.jsonPrimitive?.contentOrNull,
            attestedKeys = binding?.attestedKeys.orEmpty(),
        )
    }

    private suspend fun rememberVerificationKey(jwt: String?) {
        if (jwt.isNullOrBlank() || learnedVerificationKey != null) return
        try {
            learnedVerificationKey = verificationKeyFromJwt(jwt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.debug(error) { "Could not read a verification key from the wallet provider attestation" }
        }
    }

    private fun HttpRequestBuilder.authenticate() {
        bearerToken.trim().takeIf { it.isNotEmpty() }?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        hostHeader.trim().takeIf { it.isNotEmpty() }?.let { header(HttpHeaders.Host, it) }
    }

    private fun endpoint(path: String): String =
        "${baseUrl.trimEnd('/')}/v2/${servicePath.trim('/')}/wallet-provider-api/$path"

    private fun publicJwk(encoded: EncodedKey.Jwk): JsonObject {
        require(!encoded.privateMaterial) { "Wallet provider JWK must not contain private material" }
        return Jwk.parse(encoded).also { require(!Jwk.containsPrivateMaterial(it)) { "Wallet provider JWK must not contain private material" } }
    }

    private suspend fun distinctKeys(keys: List<JsonObject>): List<JsonObject> {
        val seen = mutableSetOf<String>()
        return keys.filter { key ->
            val encoded = EncodedKey.Jwk(BinaryData(Json.encodeToString(key).encodeToByteArray()), false)
            seen.add(Jwk.sha256Thumbprint(encoded))
        }
    }

    private data class Binding(
        val unitId: String,
        val instanceThumbprint: String,
        val attestedKeys: List<JsonObject>,
    )

    private data class ProviderTokens(
        val unitId: String,
        val wia: String?,
        val ka: String?,
        val attestedKeys: List<JsonObject>,
    )

    private companion object {
        const val MAX_ATTESTED_KEYS = 20
    }
}

/** Leaf public key from a wallet-provider attestation `x5c`, for local proof checks. */
suspend fun walletProviderVerificationKey(jwt: String, restore: suspend (pem: String) -> Key): Key {
    val x5c = CompactJws.decodeUnverified(jwt).protectedHeader["x5c"] as? JsonArray
        ?: error("Wallet provider attestation has no x5c certificate")
    val encoded = x5c.firstOrNull() as? JsonPrimitive
        ?: error("Wallet provider attestation x5c is empty")
    val body = encoded.content.chunked(64).joinToString("\n")
    return restore("-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n")
}
