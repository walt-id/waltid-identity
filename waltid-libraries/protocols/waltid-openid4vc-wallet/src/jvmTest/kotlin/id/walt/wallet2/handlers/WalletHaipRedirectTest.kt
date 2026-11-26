package id.walt.wallet2.handlers

import com.sun.net.httpserver.HttpServer
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.http.URLBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WalletHaipRedirectTest {
    @Test
    fun previewRequiresSignedRequestByReferenceAndEncryptedResponseForHaipLinks() = runBlocking<Unit> {
        val verifier = JWKKey.generate(KeyType.secp256r1)
        val signingKey = verifier.getPublicKey().exportJWKObject()
        val encryptionKey = buildJsonObject {
            JWKKey.generate(KeyType.secp256r1).getPublicKey().exportJWKObject().forEach { (name, value) ->
                put(name, value)
            }
            put("alg", "ECDH-ES")
            put("use", "enc")
            put("kid", "encryption")
        }
        val metadata = ClientMetadata(jwks = ClientMetadata.Jwks(listOf(signingKey, encryptionKey)))
        val trust = ClientIdTrustConfiguration(preRegisteredClients = mapOf("verifier" to metadata))
        val wallet = Wallet(
            "haip-test",
            staticKey = JWKKey.generate(KeyType.secp256r1),
            credentialStores = listOf(InMemoryCredentialStore()),
        )
        val credential = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2).second
        wallet.addCredential(StoredCredential("pid", credential))

        suspend fun signed(mode: String) = verifier.signJws(buildJsonObject {
            put("client_id", "verifier")
            put("aud", AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE)
            put("nonce", "presentation-nonce")
            put("response_type", "vp_token")
            put("response_uri", "https://verifier.example/response")
            put("response_mode", mode)
            put("dcql_query", Json.parseToJsonElement("""{"credentials":[{"id":"pid","format":"dc+sd-jwt","meta":{"vct_values":["https://credentials.example.com/identity_credential"]}}]}"""))
        }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")))

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun serve(path: String, contentType: String, body: String) {
            server.createContext(path) { exchange ->
                exchange.use {
                    val bytes = body.encodeToByteArray()
                    it.responseHeaders.add("Content-Type", contentType)
                    it.sendResponseHeaders(200, bytes.size.toLong())
                    it.responseBody.write(bytes)
                }
            }
        }
        val jar = signed("direct_post.jwt")
        serve("/protected", "application/oauth-authz-req+jwt", jar)
        serve("/clear", "application/oauth-authz-req+jwt", signed("direct_post"))
        serve("/unsigned", "application/json", "{}")
        server.start()
        try {
            suspend fun preview(scheme: String, parameter: String, value: String): PreviewPresentationResult {
                val url = URLBuilder("$scheme://authorize").apply {
                    parameters.append("client_id", "verifier")
                    parameters.append(parameter, value)
                }.build()
                return WalletPresentationHandler.previewPresentationWithTrust(
                    wallet, PreviewPresentationRequest(url),
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(),
                    clientIdTrustConfiguration = trust,
                )
            }
            val origin = "http://127.0.0.1:${server.address.port}"
            assertIs<PreviewPresentationResult.Ready>(preview("haip-vp", "request_uri", "$origin/protected"))
            assertEquals("haip-vp requires a signed Request Object by reference (request_uri)",
                assertFailsWith<IllegalArgumentException> { preview("haip-vp", "request", jar) }.message)
            assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
                preview("haip-vp", "request_uri", "$origin/unsigned")
            }
            assertEquals("haip-vp requires encrypted direct_post.jwt responses",
                assertFailsWith<IllegalArgumentException> { preview("haip-vp", "request_uri", "$origin/clear") }.message)
            assertIs<PreviewPresentationResult.Ready>(preview("openid4vp", "request_uri", "$origin/clear"))
        } finally {
            server.stop(0)
        }
    }
}
