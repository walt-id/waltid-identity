package id.walt.walletdemo.ios

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertTrue

class WalletDemoBridgeE2ETest {

    private val enterpriseBase = "http://localhost:7500"
    private val hostHeader = "waltid.enterprise.localhost"
    private val tenantPath = "waltid.waltid-tenant01"
    private val issuerRef = "$tenantPath.issuer2.mdl-profile"
    private val walletPath = "$tenantPath.wallet"
    private val verifierRef = "$tenantPath.verifier2"
    private val attesterRef = "$tenantPath.client-attester"
    private val keyRef = "$tenantPath.kms.wallet_key"

    private val httpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun login(): String {
        val resp = httpClient.post("$enterpriseBase/auth/account/emailpass") {
            header("Host", hostHeader)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin@walt.id","password":"admin123456"}""")
        }
        val body = Json.parseToJsonElement(resp.body<String>()).jsonObject
        return body["token"]?.jsonPrimitive?.content
            ?: error("Login failed: $body")
    }

    private suspend fun createCredentialOffer(token: String): String {
        val resp = httpClient.post("$enterpriseBase/v2/$issuerRef/issuer-service-api/credentials/offers") {
            header("Host", hostHeader)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"authMethod":"PRE_AUTHORIZED"}""")
        }
        val body = Json.parseToJsonElement(resp.body<String>()).jsonObject
        return body["credentialOffer"]?.jsonPrimitive?.content
            ?: body["offerUrl"]?.jsonPrimitive?.content
            ?: error("Failed to create offer: $body")
    }

    private suspend fun createVerificationSession(token: String): String {
        val requestBody = """
        {
            "flow_type": "cross_device",
            "core_flow": {
                "dcql_query": {
                    "credentials": [{
                        "id": "my_mdl",
                        "format": "mso_mdoc",
                        "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
                        "claims": [
                            { "path": ["org.iso.18013.5.1", "family_name"] },
                            { "path": ["org.iso.18013.5.1", "given_name"] }
                        ]
                    }]
                },
                "policies": {
                    "vc_policies": [{ "policy": "signature" }]
                }
            }
        }
        """.trimIndent()
        val resp = httpClient.post("$enterpriseBase/v1/$verifierRef/verifier2-service-api/verification-session/create") {
            header("Host", hostHeader)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        val body = Json.parseToJsonElement(resp.body<String>()).jsonObject
        return body["bootstrapAuthorizationRequestUrl"]?.jsonPrimitive?.content
            ?: body["requestUrl"]?.jsonPrimitive?.content
            ?: error("Failed to create verification session: $body")
    }

    @Test
    fun testEnterpriseReceiveAndPresent() = runTest {
        // 1. Login to get bearer token
        val token = login()
        assertTrue(token.isNotEmpty(), "Bearer token should not be empty")
        println("Login OK, token length=${token.length}")

        // 2. Configure bridge with enterprise environment
        val bridge = WalletDemoBridgeController()
        bridge.updateEnvironment(
            baseUrl = enterpriseBase,
            walletPath = walletPath,
            hostHeader = hostHeader,
            bearerToken = token,
            attesterRef = attesterRef,
            keyRef = keyRef,
        )

        // 3. Create credential offer via enterprise API
        val offerUrl = createCredentialOffer(token)
        assertTrue(offerUrl.startsWith("openid-credential-offer://"), "Offer URL should start with openid-credential-offer://, got: $offerUrl")
        println("Offer created: ${offerUrl.take(80)}...")

        // 4. Receive credential via enterprise wallet service (server-side key)
        val receiveResult = bridge.enterpriseReceive(offerUrl)
        assertTrue(receiveResult.success, "Enterprise receive should succeed: ${receiveResult.message}")
        println("Receive OK: ${receiveResult.message}")

        // 5. Create verification session
        val requestUrl = createVerificationSession(token)
        assertTrue(requestUrl.isNotEmpty(), "Verification request URL should not be empty")
        println("Verification session created: ${requestUrl.take(80)}...")

        // 6. Present credential via enterprise wallet service
        val presentResult = bridge.enterprisePresent(requestUrl)
        assertTrue(presentResult.success, "Enterprise present should succeed: ${presentResult.message}")
        println("Present OK: ${presentResult.message}")

        println("\n=== iOS E2E TEST PASSED: Full enterprise receive + present cycle ===")
    }
}
