package id.walt.wallet2.mobile.test

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Uses the EUDI reference wallet's public mock provider, confined to device integration tests.
 * Its claims exercise test interoperability and do not establish hardware or certification assurance.
 * See https://github.com/eu-digital-identity-wallet/eudi-srv-wallet-provider.
 */
internal class EudiTestKeyAttestationProvider private constructor(
    override val verificationKey: Key,
) : KeyAttestationProvider {
    override suspend fun attest(request: KeyAttestationRequest): String {
        require(request.credentialIssuer == "https://issuer.eudiw.dev")
        val payload = buildJsonObject {
            putJsonObject("jwkSet") {
                putJsonArray("keys") {
                    add(Json.parseToJsonElement(request.proofKey.data.toByteArray().decodeToString()))
                }
            }
            request.nonce?.let { put("nonce", it) }
            putJsonArray("supportedSigningAlgorithms") { add("ES256") }
        }
        return request("/key-attestation/jwk-set", payload).getValue("keyAttestation").jsonPrimitive.content
    }

    companion object {
        private const val ORIGIN = "https://wallet-provider.eudiw.dev"

        suspend fun create(): EudiTestKeyAttestationProvider {
            // Resolve the verification key from the configured HTTPS service, not the returned JWT.
            val jwk = request("/jwks").getValue("keys").jsonArray.single().jsonObject
            val key = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
                EncodedKey.Jwk(BinaryData(jwk.toString().encodeToByteArray()), false)
                    .toStoredSoftwareKey(KeyId("eudi-test-attester"), setOf(KeyUsage.VERIFY)),
            )
            return EudiTestKeyAttestationProvider(key)
        }

        private suspend fun request(path: String, payload: JsonObject? = null): JsonObject =
            HttpClient(Android) {
                install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            }.use { client ->
                val response = if (payload == null) client.get(ORIGIN + path) else client.post(ORIGIN + path) {
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }
                check(response.status == HttpStatusCode.OK) { "EUDI test attester returned HTTP ${response.status.value}" }
                Json.parseToJsonElement(response.bodyAsText()).jsonObject
            }
    }
}
