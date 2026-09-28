package id.walt.wallet2.mobile

import com.sun.net.httpserver.HttpServer
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.persistence.keys.*
import io.ktor.http.URLBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

/** Real reviewed wallet submission and HTTP delivery; native authentication is simulated here. */
class NativeScaDirectPostTest {
    @Test
    fun reviewedOrdinaryPaymentDeliversTheSignedProof() = runTest {
        val software = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId("synthetic-holder"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
        )
        val publicKey = requireNotNull(software.capabilities.publicKeyExporter).exportPublicKey()
        val key = object : ManagedKey {
            override val storedKey = StoredKey.Managed(
                StoredKey.CURRENT_VERSION, software.id, software.spec, software.usages,
                ProviderId("synthetic-platform"), 1, BinaryData(byteArrayOf(1)),
                publicKey,
            )
            override val capabilities = software.capabilities.copy(privateKeyExporter = null)
        }
        val provider = object : PlatformManagedKeyProvider {
            override fun keyUseAuthorizationPolicy(stored: StoredKey.Managed) = KeyUseAuthorizationPolicy.BiometricCurrentSet
            override suspend fun keyFacts(stored: StoredKey.Managed) = PlatformKeyFacts(
                origin = KeyOrigin.GENERATED, protection = KeyProtectionLevel.HARDWARE,
                securityLevel = KeySecurityLevel.TRUSTED_ENVIRONMENT,
                authorizationEvidence = KeyAuthorizationEvidence.NATIVE_ATTRIBUTES,
            )
            override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport = error("Unused")
            override suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey = error("Unused")
            override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration = error("Unused")
            override suspend fun deleteManagedKey(stored: StoredKey.Managed): Unit = error("Unused")
        }
        val fixture = scaWalletFixture(key, provider)
        val received = AtomicReference<Map<String, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/response") { exchange ->
            received.set(exchange.requestBody.bufferedReader().readText().split("&").associate { item ->
                val pair = item.split("=", limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            })
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.start()
        try {
            val endpoint = "http://127.0.0.1:${server.address.port}/response"
            val request = URLBuilder("openid4vp://authorize").apply {
                fixture.request.forEach { (name, value) ->
                    parameters.append(name, if (value is JsonPrimitive) value.content else value.toString())
                }
                parameters["response_mode"] = "direct_post"
                parameters["response_uri"] = endpoint
                parameters["client_id"] = "redirect_uri:$endpoint"
            }.buildString()
            val preview = assertIs<MobileWalletPresentationPreviewResult.Ready>(fixture.wallet.previewPresentation(request)).preview
            fixture.wallet.submitPresentation(preview.previewHandle, preview.credentialOptions.map {
                MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId)
            })
            val vp = Json.parseToJsonElement(assertNotNull(received.get())["vp_token"]!!)
                .jsonObject.getValue("payment").jsonArray.single().jsonPrimitive.content
            val proof = CompactJws.verify(vp.substringAfterLast('~'), key, JwsAlgorithm.ES256)
            val claims = Json.parseToJsonElement(proof.payload.decodeToString()).jsonObject
            assertEquals("redirect_uri:$endpoint", claims["aud"]?.jsonPrimitive?.content)
            assertEquals("direct_post", claims["response_mode"]?.jsonPrimitive?.content)
            assertEquals(2, claims["amr"]?.jsonArray?.size)
            assertFalse(claims["jti"]?.jsonPrimitive?.content.isNullOrBlank())
        } finally { server.stop(0) }
    }
}
