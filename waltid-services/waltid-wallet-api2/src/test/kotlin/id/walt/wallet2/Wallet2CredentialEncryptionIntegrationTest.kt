package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.controller.Issuer2RouteSurface
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialResult
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import id.walt.openid4vci.CredentialFormat as VciCredentialFormat

/**
 * Wallet2 claims a credential from Issuer2 when the issuer advertises credential encryption.
 * The stored credential is the decrypted document, and the credential endpoint itself carries
 * `application/jwt` in both directions.
 */
class Wallet2CredentialEncryptionIntegrationTest {
    private val issuerKey = runBlocking { JWKKey.generate(KeyType.secp256r1) }

    @Test
    fun `wallet receives an encrypted credential from issuer2`() {
        val port = freePort()
        val baseUrl = "http://127.0.0.1:$port"
        val configurationId = "encrypted_identity"
        val wire = CredentialWire()
        val configuration = CredentialConfiguration(
            format = VciCredentialFormat.SD_JWT_VC,
            vct = "https://credentials.example/encrypted-identity",
            scope = configurationId,
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf("jwt" to ProofTypeMetadata(proofSigningAlgValuesSupported = setOf("ES256"))),
        )
        val profile = CredentialProfileConfig(
            name = "encrypted-profile",
            credentialConfigurationId = configurationId,
            issuerKey = Json.parseToJsonElement(KeySerialization.serializeKey(issuerKey)).jsonObject,
            issuerDid = runBlocking { DidKeyRegistrar().registerByKey(issuerKey, DidKeyCreateOptions()).did },
            credentialData = buildJsonObject { put("given_name", "Ada") },
        )
        val issuer = Issuer2Module(
            Issuer2ServiceConfig(baseUrl, credentialEncryptionKey = encryptionKey()),
            Issuer2MetadataConfig(credentialConfigurations = mapOf(configurationId to Json.encodeToJsonElement(configuration))),
            Issuer2ProfilesConfig(profiles = mapOf(profile.name to profile)),
        )

        E2ETest("127.0.0.1", port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig("wallet-service", OSSWallet2ServiceConfig(publicBaseUrl = Url(baseUrl)))
            },
            init = { DidService.minimalInit() },
            module = {
                install(CallId) { generate { Uuid.random().toString() } }
                install(SSE)
                wallet2Module(withPlugins = false)
                routing {
                    intercept(ApplicationCallPipeline.Call) {
                        val path = context.request.path()
                        val requestType = context.request.contentType().withoutParameters().toString()
                        if (context.request.httpMethod == HttpMethod.Post) wire.posts += "$path $requestType"
                        proceed()
                        if (context.request.httpMethod == HttpMethod.Post && path.endsWith("/credential")) {
                            wire.requestTypes += requestType
                            wire.responseTypes += context.response.headers[HttpHeaders.ContentType]
                                ?.substringBefore(';')
                                ?.trim()
                                ?.lowercase()
                                .orEmpty()
                        }
                    }
                    issuer.managementController.register(this)
                    issuer.openId4VciController.register(this, Issuer2RouteSurface.preAuthorizedCodeOnly)
                }
            },
        ) {
            testAndReturn("wallet receives an encrypted credential") {
            testHttpClient().use { http ->
                val walletId = http.post("/wallet") { setBody(CreateWalletRequest()) }
                    .also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }
                    .body<WalletCreatedResponse>().walletId
                try {
                    val keyId = http.post("/wallet/$walletId/keys/generate") {
                        setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(KeyType.secp256r1))
                    }.also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }
                        .body<WalletKeyInfo>().keyId
                    val receipt = http.post("/issuer2/credential-offers") {
                        setBody(buildJsonObject {
                            put("authMethod", AuthenticationMethod.PRE_AUTHORIZED.name)
                            put("valueMode", "BY_VALUE")
                            put("profileId", profile.name)
                        })
                    }.also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }
                        .body<JsonObject>()
                    val received = http.post("/wallet/$walletId/credentials/receive") {
                        setBody(ReceiveCredentialRequest(
                            offerUrl = Url(receipt.getValue("credentialOffer").jsonPrimitive.content),
                            keyId = keyId,
                        ))
                    }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<ReceiveCredentialResult>()
                    val credentialId = received.credentialIds.single()
                    val stored = http.get("/wallet/$walletId/credentials/$credentialId")
                        .also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                        .body<StoredCredential>()

                    assertEquals("Ada", stored.credential.credentialData.getValue("given_name").jsonPrimitive.content)
                    assertEquals(listOf("application/jwt"), wire.requestTypes.map { it.lowercase() }, "requests=$wire")
                    assertEquals(listOf("application/jwt"), wire.responseTypes, "responses=$wire")
                } finally {
                    assertTrue(http.delete("/wallet/$walletId").status.isSuccess())
                }
            }
            }
        }
    }

    private fun encryptionKey(): String = runBlocking {
        val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("credential-request-encryption"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            ),
        )
        StoredKeyCodec.encodeToString(key.storedKey)
    }
}

private class CredentialWire {
    val posts = mutableListOf<String>()
    val requestTypes = mutableListOf<String>()
    val responseTypes = mutableListOf<String>()

    override fun toString(): String = "posts=$posts requests=$requestTypes responses=$responseTypes"
}
