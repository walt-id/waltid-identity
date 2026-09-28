@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.request

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.webdatafetching.WebDataFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.Base64
import kotlin.test.*

/** Nimbus is the independent verifier: it signs and encrypts what the production wallet receives. */
class EncryptedRequestObjectInteropTest {
    private val signingKey = ECKeyGenerator(Curve.P_256).keyID("verifier-signing").generate()
    private val trust = ClientIdTrustConfiguration(preRegisteredClients = mapOf(
        "verifier" to ClientMetadata(jwks = ClientMetadata.Jwks(listOf(
            Json.parseToJsonElement(signingKey.toPublicJWK().toJSONString()).jsonObject,
        ))),
    ))
    private val requestUrl = URLBuilder("openid4vp://authorize").apply {
        parameters.append("client_id", "verifier")
        parameters.append("request_uri", "https://verifier.example/request")
        parameters.append("request_uri_method", "post")
    }.build()

    @Test
    fun `Nimbus signed and encrypted requests authenticate with both advertised algorithms`() = runBlocking<Unit> {
        for (method in listOf(EncryptionMethod.A128GCM, EncryptionMethod.A256GCM)) {
            val resolved = exchange { nonce, recipient -> encrypt(sign(nonce), recipient, method) }
            assertIs<ResolvedAuthorizationRequest.AuthenticatedRequestObject>(resolved)
            assertEquals("verifier", resolved.authorizationRequest.clientId)
            assertEquals("presentation-nonce", resolved.authorizationRequest.nonce)
        }
    }

    @Test
    fun `signed unencrypted responses remain supported after advertising a key`() = runBlocking<Unit> {
        assertIs<ResolvedAuthorizationRequest.AuthenticatedRequestObject>(exchange { nonce, _ -> sign(nonce) })
        // JWT media types are case insensitive; kid is optional when the exchange has a single key.
        assertIs<ResolvedAuthorizationRequest.AuthenticatedRequestObject>(exchange { nonce, recipient ->
            encrypt(sign(nonce), recipient, contentType = "jwt", keyId = null)
        })
    }

    @Test
    fun `encryption does not bypass inner signature trust identity freshness or nonce checks`() = runBlocking<Unit> {
        val invalidClaims = listOf(
            buildJsonObject { put("wallet_nonce", "wrong") },
            buildJsonObject { put("client_id", "other") },
            buildJsonObject { put("aud", "other") },
            buildJsonObject { put("exp", 0) },
        )
        for (claims in invalidClaims) {
            assertFails { exchange { nonce, recipient -> encrypt(sign(nonce, claims), recipient) } }
        }
        assertFails {
            exchange { nonce, recipient -> encrypt(tamper(sign(nonce)), recipient) }
        }
        assertFails {
            exchange(ClientIdTrustConfiguration()) { nonce, recipient -> encrypt(sign(nonce), recipient) }
        }
    }

    @Test
    fun `rejects tampered ciphertext wrong keys and unsupported encrypted wrappers`() = runBlocking<Unit> {
        assertFails { exchange { nonce, recipient -> tamper(encrypt(sign(nonce), recipient)) } }
        assertFails {
            exchange { nonce, _ -> encrypt(sign(nonce), ECKeyGenerator(Curve.P_256).generate().toPublicJWK()) }
        }
        for (contentType in listOf(null, "json", "application/jwt")) {
            assertFails {
                exchange { nonce, recipient -> encrypt(sign(nonce), recipient, contentType = contentType) }
            }
        }
        assertFails {
            exchange { nonce, recipient -> encrypt(sign(nonce), recipient, keyId = "wrong-key") }
        }
        // A valid Nimbus JWE using an algorithm we did not advertise must be rejected.
        assertFails {
            exchange { nonce, recipient -> encrypt(sign(nonce), recipient, algorithm = JWEAlgorithm.ECDH_ES_A128KW) }
        }
        assertFails {
            exchange { nonce, recipient -> encrypt(sign(nonce), recipient, method = EncryptionMethod.A128CBC_HS256) }
        }
    }

    @Test
    fun `encrypted unsigned JWT or JSON is rejected even with permissive unsigned policy`() = runBlocking<Unit> {
        for (payload in listOf("{\"client_id\":\"verifier\"}", unsignedJwt())) {
            assertFails { exchange { _, recipient -> encrypt(payload, recipient) } }
        }
    }

    @Test
    fun `each concurrent exchange has an independent key and rejects another exchanges ciphertext`() = runBlocking<Unit> {
        val recipients = mutableListOf<ECKey>()
        val nonces = mutableListOf<String>()
        val bothArrived = CompletableDeferred<Unit>()
        val lock = Mutex()
        val work = (0..1).map {
            async {
                assertFails {
                    exchange { nonce, recipient ->
                        val index = lock.withLock {
                            val index = recipients.size
                            recipients += recipient
                            nonces += nonce
                            if (recipients.size == 2) bothArrived.complete(Unit)
                            index
                        }
                        bothArrived.await()
                        encrypt(sign(nonce), recipients[1 - index])
                    }
                }
            }
        }
        work.awaitAll()
        assertEquals(2, recipients.map { it.x.toString() }.distinct().size)
        assertEquals(2, nonces.distinct().size)
    }

    @Test
    fun `metadata opt out and GET preserve their existing fetch contract`() = runBlocking<Unit> {
        for (method in listOf(RequestUriHttpMethod.POST, RequestUriHttpMethod.GET)) {
            HttpClient(MockEngine { request ->
                if (method == RequestUriHttpMethod.POST) {
                    assertNull(parseQueryString((request.body as TextContent).text)["wallet_metadata"])
                }
                respond("unchanged", headers = headersOf(HttpHeaders.ContentType, "application/oauth-authz-req+jwt"))
            }).use { client ->
                val response = AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
                    WebDataFetcher.wrapping(client, id = "encrypted-request-opt-out-test"),
                    "https://verifier.example/request", method, sendWalletMetadata = false,
                )
                assertEquals("unchanged", response.body)
            }
        }
    }

    private suspend fun exchange(
        configuration: ClientIdTrustConfiguration = trust,
        response: suspend (String, ECKey) -> String,
    ): ResolvedAuthorizationRequest = HttpClient(MockEngine { request ->
        val form = parseQueryString((request.body as TextContent).text)
        val metadata = Json.parseToJsonElement(requireNotNull(form["wallet_metadata"])).jsonObject
        assertEquals("kept", metadata.getValue("custom_metadata").jsonPrimitive.content)
        assertFalse("jwks_uri" in metadata)
        assertEquals(listOf("ECDH-ES"), metadata.getValue("request_object_encryption_alg_values_supported")
            .jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("A128GCM", "A256GCM"), metadata.getValue("request_object_encryption_enc_values_supported")
            .jsonArray.map { it.jsonPrimitive.content })
        val publicJwk = metadata.getValue("jwks").jsonObject.getValue("keys").jsonArray.single().jsonObject
        assertFalse("d" in publicJwk)
        assertEquals("enc", publicJwk.getValue("use").jsonPrimitive.content)
        assertEquals("ECDH-ES", publicJwk.getValue("alg").jsonPrimitive.content)
        respond(response(requireNotNull(form["wallet_nonce"]), ECKey.parse(publicJwk.toString())),
            headers = headersOf(HttpHeaders.ContentType, "application/oauth-authz-req+jwt"))
    }).use { client ->
        AuthorizationRequestResolver.resolve(requestUrl, trustConfiguration = configuration, fetchRequestUri = { uri, method ->
            AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
                WebDataFetcher.wrapping(client, id = "encrypted-request-interop-test"), uri, method,
                requestUriPostWalletMetadata = "{\"custom_metadata\":\"kept\",\"jwks_uri\":\"https://unused.example/keys\"}",
            )
        })
    }

    private fun sign(nonce: String, overrides: JsonObject = JsonObject(emptyMap())): String {
        val payload = buildJsonObject {
            put("client_id", "verifier")
            put("aud", AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE)
            put("nonce", "presentation-nonce")
            put("wallet_nonce", nonce)
            put("response_type", "vp_token")
            put("response_mode", "direct_post")
            put("response_uri", "https://verifier.example/response")
            overrides.forEach { (name, value) -> put(name, value) }
        }
        return JWSObject(JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("oauth-authz-req+jwt")).keyID(signingKey.keyID).build(), Payload(payload.toString()))
            .apply { sign(ECDSASigner(signingKey)) }.serialize()
    }

    private fun encrypt(
        signed: String, recipient: ECKey,
        method: EncryptionMethod = EncryptionMethod.A128GCM,
        contentType: String? = "JWT", keyId: String? = recipient.keyID,
        algorithm: JWEAlgorithm = JWEAlgorithm.ECDH_ES,
    ): String = JWEObject(JWEHeader.Builder(algorithm, method).contentType(contentType).keyID(keyId).build(), Payload(signed))
        .apply { encrypt(ECDHEncrypter(recipient)) }.serialize()

    private fun tamper(jwt: String): String = jwt.split('.').toMutableList().apply {
        this[lastIndex] = (if (last().first() == 'A') "B" else "A") + last().drop(1)
    }.joinToString(".")

    private fun unsignedJwt(): String {
        val base64 = Base64.getUrlEncoder().withoutPadding()
        return base64.encodeToString("{\"alg\":\"none\"}".toByteArray()) + "." +
            base64.encodeToString("{\"client_id\":\"verifier\"}".toByteArray()) + "."
    }
}
