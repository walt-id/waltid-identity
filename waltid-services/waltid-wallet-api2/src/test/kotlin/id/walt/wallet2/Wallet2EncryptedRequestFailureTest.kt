package id.walt.wallet2

import com.sun.net.httpserver.HttpServer
import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidService
import id.walt.wallet2.handlers.ResolveVpRequestRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class Wallet2EncryptedRequestFailureTest {
    @Test
    fun `invalid encryption and rejected retrieval return 400 through the wallet API`() {
        val host = "127.0.0.1"
        val requestPort = freePort()
        val walletKey = runBlocking {
            KeySerialization.serializeKeyToJson(JWKKey.generate(KeyType.secp256r1)).jsonObject
        }
        val encryptedRequest = runBlocking {
            val otherKey = JWKKey.generate(KeyType.secp256r1).getPublicKey().exportJWKObject()
            CompactJwe.encrypt(
                plaintext = "signed.request.object".encodeToByteArray(),
                recipientPublicKey = EncodedKey.Jwk(BinaryData(otherKey.toString().encodeToByteArray()), privateMaterial = false),
                contentEncryption = JweContentEncryption.A128GCM,
                protectedHeader = buildJsonObject { put("cty", "JWT") },
            )
        }.encodeToByteArray()
        val verifier = HttpServer.create(InetSocketAddress(host, requestPort), 0).apply {
            createContext("/request") { exchange ->
                exchange.requestBody.use { it.readBytes() }
                val rejected = exchange.requestURI.query == "rejected"
                val body = if (rejected) "Wallet encryption key required".encodeToByteArray() else encryptedRequest
                exchange.responseHeaders.add("Content-Type", "application/oauth-authz-req+jwt")
                exchange.sendResponseHeaders(if (rejected) 400 else 200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val walletPort = freePort()
            E2ETest(host, walletPort, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(
                        publicBaseUrl = Url("http://$host:$walletPort"),
                    ))
                },
                init = {
                    DidService.minimalInit()
                    OSSWallet2Service.configureInMemory()
                },
                module = { wallet2Module(withPlugins = false) },
            ) {
                val http = testHttpClient()
                testAndReturn("Invalid Request Objects are HTTP 400 input errors") {
                    val created = http.post("/wallet") { setBody(CreateWalletRequest(staticKey = walletKey)) }
                    assertEquals(HttpStatusCode.Created, created.status)
                    val walletId = Json.parseToJsonElement(created.bodyAsText()).jsonObject.getValue("walletId").jsonPrimitive.content
                    for (rejected in listOf(false, true)) {
                        val requestUrl = URLBuilder("openid4vp://authorize").apply {
                            parameters.append("client_id", "redirect_uri:https://verifier.example/response")
                            parameters.append("request_uri", "http://$host:$requestPort/request${if (rejected) "?rejected" else ""}")
                            parameters.append("request_uri_method", "post")
                        }.build()
                        val response = http.post("/wallet/$walletId/credentials/present/resolve-request") {
                            contentType(ContentType.Application.Json)
                            setBody(ResolveVpRequestRequest(requestUrl = requestUrl))
                        }
                        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
                        assertTrue(response.bodyAsText().contains(
                            if (rejected) "AuthorizationRequest cannot be retrieved" else "Could not decrypt encrypted Request Object",
                        ))
                    }
                }
            }
        } finally {
            verifier.stop(0)
        }
    }
}
