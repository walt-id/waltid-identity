package id.walt.wallet2.handlers

import com.sun.net.httpserver.HttpServer
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WalletRequestIssuerTest {
    @Test
    fun `direct presenter and Wallet2 preview propagate the expected audience to POST metadata`() = runBlocking {
        val verifierKey = JWKKey.generate(KeyType.secp256r1)
        val trust = ClientIdTrustConfiguration(preRegisteredClients = mapOf(
            "verifier" to ClientMetadata(jwks = ClientMetadata.Jwks(listOf(verifierKey.getPublicKey().exportJWKObject()))),
        ))
        val wallet = Wallet(
            id = "issuer-test",
            staticKey = JWKKey.generate(KeyType.secp256r1),
            credentialStores = listOf(InMemoryCredentialStore()),
        )
        val advertisedIssuers = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/request") { exchange ->
            exchange.use {
                val form = parseQueryString(it.requestBody.readAllBytes().decodeToString())
                val metadata = Json.parseToJsonElement(requireNotNull(form["wallet_metadata"])).jsonObject
                val issuer = metadata.getValue("issuer").jsonPrimitive.content
                advertisedIssuers += issuer
                val payload = buildJsonObject {
                    put("client_id", "verifier")
                    put("aud", issuer)
                    put("wallet_nonce", requireNotNull(form["wallet_nonce"]))
                    put("nonce", "presentation-nonce")
                    put("response_type", "vp_token")
                    put("response_mode", "direct_post")
                    put("response_uri", "https://verifier.example/response")
                    put("dcql_query", Json.parseToJsonElement("""{"credentials":[{"id":"pid","format":"jwt_vc_json","meta":{"type_values":[["VerifiableCredential"]]}}]}"""))
                }
                val jwt = runBlocking {
                    verifierKey.signJws(
                        payload.toString().encodeToByteArray(),
                        mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
                    )
                }.encodeToByteArray()
                it.responseHeaders.add("Content-Type", "application/oauth-authz-req+jwt")
                it.sendResponseHeaders(200, jwt.size.toLong())
                it.responseBody.write(jwt)
            }
        }
        server.start()
        try {
            val url = URLBuilder("openid4vp://authorize").apply {
                parameters.append("client_id", "verifier")
                parameters.append("request_uri", "http://127.0.0.1:${server.address.port}/request")
                parameters.append("request_uri_method", "post")
            }.build()
            for (audience in listOf(AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE, "https://wallet.example")) {
                val direct = WalletPresentFunctionality2.resolveAuthorizationRequest(
                    presentationRequestUrl = url,
                    unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
                    legacyFallbackCallback = null,
                    clientIdTrustConfiguration = trust,
                    expectedRequestObjectAudience = audience,
                )
                assertEquals("verifier", direct.clientId)
                val preview = WalletPresentationHandler.previewPresentationWithTrust(
                    wallet = wallet,
                    request = PreviewPresentationRequest(url),
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(),
                    clientIdTrustConfiguration = trust,
                    expectedRequestObjectAudience = audience,
                )
                // An empty wallet cannot satisfy the query, but request authentication must succeed.
                assertIs<ResolvedAuthorizationRequest.AuthenticatedRequestObject>(preview.resolvedAuthorizationRequest)
                assertEquals(listOf(audience, audience), advertisedIssuers.takeLast(2))
            }
        } finally {
            server.stop(0)
        }
    }
}
