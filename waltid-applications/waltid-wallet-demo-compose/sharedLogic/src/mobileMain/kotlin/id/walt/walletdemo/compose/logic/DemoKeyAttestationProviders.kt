package id.walt.walletdemo.compose.logic

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationProviderResolver
import id.walt.wallet2.handlers.KeyAttestationRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.time.Clock

/**
 * Demo-only test attesters. These signed assertions exercise interoperability and do not establish
 * hardware protection, user authentication, certification or production Wallet Unit Attestation.
 * Unknown issuers never receive synthetic attestations.
 */
internal class DemoKeyAttestationProviders(
    private val clientFactory: () -> HttpClient = {
        HttpClient {
            followRedirects = false
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }
    },
) : KeyAttestationProviderResolver {
    override suspend fun resolve(credentialIssuer: String): KeyAttestationProvider? = when (credentialIssuer) {
        ITB_ISSUER -> ItbDemoKeyAttester(
            CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(KeyId("demo-itb-attester"), KeySpec.Ec(EcCurve.P256),
                    setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
            ),
        )
        EUDI_ISSUER -> {
            val jwk = httpRequest("/jwks").getValue("keys").jsonArray.single().jsonObject
            val key = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
                EncodedKey.Jwk(BinaryData(jwk.toString().encodeToByteArray()), false)
                    .toStoredSoftwareKey(KeyId("demo-eudi-attester"), setOf(KeyUsage.VERIFY)),
            )
            object : KeyAttestationProvider {
                override val verificationKey = key
                override suspend fun attest(request: KeyAttestationRequest): String {
                    require(request.credentialIssuer == EUDI_ISSUER)
                    val payload = buildJsonObject {
                        putJsonObject("jwkSet") {
                            putJsonArray("keys") {
                                add(Json.parseToJsonElement(request.proofKey.data.toByteArray().decodeToString()))
                            }
                        }
                        request.nonce?.let { put("nonce", it) }
                        putJsonArray("supportedSigningAlgorithms") { add("ES256") }
                    }
                    return httpRequest("/key-attestation/jwk-set", payload).getValue("keyAttestation").jsonPrimitive.content
                }
            }
        }
        else -> null
    }

    private suspend fun httpRequest(path: String, payload: JsonObject? = null): JsonObject =
        clientFactory().use { client ->
            val response = if (payload == null) client.get(EUDI_PROVIDER + path)
            else client.post(EUDI_PROVIDER + path) {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            check(response.status == HttpStatusCode.OK) {
                "EUDI demo key attester returned HTTP ${response.status.value}"
            }
            Json.parseToJsonElement(response.bodyAsText()).jsonObject
        }

    companion object {
        const val ITB_ISSUER = "https://dev-i4mlab.aegean.gr/rfc-issuer"
        const val EUDI_ISSUER = "https://issuer.eudiw.dev"
        private const val EUDI_PROVIDER = "https://wallet-provider.eudiw.dev"
    }
}

private class ItbDemoKeyAttester(override val verificationKey: Key) : KeyAttestationProvider {
    override suspend fun attest(request: KeyAttestationRequest): String {
        require(request.credentialIssuer == DemoKeyAttestationProviders.ITB_ISSUER)
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        val jwk = verificationKey.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(verificationKey.spec)
        val header = buildJsonObject {
            put("typ", "key-attestation+jwt")
            put("jwk", Json.parseToJsonElement(jwk.data.toByteArray().decodeToString()))
        }
        val payload = buildJsonObject {
            put("iat", now)
            put("exp", now + 300)
            request.nonce?.let { put("nonce", it) }
            putJsonArray("attested_keys") {
                add(Json.parseToJsonElement(request.proofKey.data.toByteArray().decodeToString()))
            }
            putJsonArray("key_storage") { add("https://example.invalid/walt-id/itb/key-storage-unassessed") }
            putJsonArray("user_authentication") { add("https://example.invalid/walt-id/itb/user-authentication-unassessed") }
            put("certification", "https://example.invalid/walt-id/itb/no-certification")
            putJsonObject("key_storage_status") {
                putJsonObject("status") {
                    putJsonObject("status_list") {
                        put("uri", "https://example.invalid/walt-id/itb/no-status-list")
                        put("idx", 0)
                    }
                }
                put("exp", now + 3600)
            }
        }
        return CompactJws.sign(payload.toString().encodeToByteArray(), verificationKey, JwsAlgorithm.ES256, header)
    }
}
