package id.walt.wallet2.consent

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.json.*

internal const val ISSUER = "https://issuer.example"
internal class PaymentConsentFixture private constructor(val runtime: CryptoRuntime, val issuerKey: Key) {
    val payload = Json.parseToJsonElement("""{"transaction_id":"txn-1","payee":{"name":"Super Store","id":"merchant-001"},"currency":"EUR","amount":11.56}""").jsonObject
    var metadata = buildJsonObject {
        put("vct", "$ISSUER/vct"); put("category", "urn:eu:europa:ec:eudi:sua:sca")
        put("transaction_data_types", buildJsonObject {
            put("urn:eudi:sca:payment:1", buildJsonObject {
                put("schema", "urn:eudi:sca:payment:1")
                put("claims", JsonArray(listOf(listOf("transaction_id"), listOf("payee", "name"), listOf("payee", "id"), listOf("currency"), listOf("amount")).map { path -> buildJsonObject {
                    put("path", JsonArray(path.map(::JsonPrimitive)))
                    put("display", buildJsonArray {
                        add(buildJsonObject { put("locale", "en"); put("label", path.last()) })
                        add(buildJsonObject { put("locale", "de"); put("label", "DE ${path.last()}") })
                    })
                } }))
                put("ui_labels", buildJsonObject { put("affirmative_action_label", buildJsonArray {
                    add(buildJsonObject { put("lang", "en"); put("value", "Pay") })
                    add(buildJsonObject { put("lang", "de"); put("value", "Zahlen") })
                }) })
            })
        })
    }
    var onMetadataRequest: suspend () -> Unit = {}
    val requests = mutableListOf<String>()
    val requestUrls = mutableListOf<String>()
    val requestHeaders = mutableListOf<Headers>()
    val documents = mutableMapOf<String, String>()
    val client = HttpClient(MockEngine { request ->
        onMetadataRequest()
        requests += request.url.encodedPath
        requestUrls += request.url.toString()
        requestHeaders += request.headers
        val body = if (request.url.encodedPath == "/vct") metadata.toString() else documents.getValue(request.url.encodedPath)
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    })
    lateinit var resolver: PaymentConsentResolver
        private set
    suspend fun credential(extra: JsonObject = JsonObject(emptyMap())): SdJwtCredential {
        val claims = buildJsonObject {
            put("iss", ISSUER); put("vct", "$ISSUER/vct"); put("iat", 1); put("exp", 99_999_999_999)
            put("_sd_alg", "sha-256"); put("_sd", JsonArray(emptyList()))
            extra.forEach { (name, value) -> put(name, value) }
        }
        val jwt = CompactJws.sign(claims.toString().encodeToByteArray(), issuerKey, JwsAlgorithm.ES256,
            buildJsonObject { put("typ", "dc+sd-jwt") })
        return CredentialParser.detectAndParse("$jwt~").second as SdJwtCredential
    }
    suspend fun close() { client.close(); runtime.close() }
    companion object {
        suspend fun create(): PaymentConsentFixture {
            val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
            val key = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(KeyId("issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
            return PaymentConsentFixture(runtime, key).also { fixture ->
                val public = requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(key.spec)
                fixture.resolver = PaymentConsentResolver(listOf(PaymentCredentialIssuer(ISSUER, public.data.toByteArray().decodeToString())), fixture.client)
            }
        }
    }
}
