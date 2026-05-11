package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.test.integration.expectLooksLikeJwt
import id.walt.test.integration.expectSuccess
import id.walt.test.integration.loadJsonResource
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for raw JWT signing functionality.
 * Tests the /raw/jwt/sign endpoint for signing credentials without the full OID4VCI flow.
 */
class RawJwtSigningIntegrationTest : AbstractIntegrationTest() {

    private val issuerKey = loadJsonResource("issuance/key.json")
    private val issuerDid = loadResource("issuance/did.txt")

    @Test
    fun shouldSignRawJwtCredential() = runTest {
        val client = environment.testHttpClient()
        
        val signRequest = buildJsonObject {
            put("issuerKey", issuerKey)
            put("issuerDid", JsonPrimitive(issuerDid))
            put("subjectDid", JsonPrimitive("did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"))
            putJsonObject("credentialData") {
                putJsonArray("@context") {
                    add(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))
                }
                putJsonArray("type") {
                    add(JsonPrimitive("VerifiableCredential"))
                    add(JsonPrimitive("TestCredential"))
                }
                putJsonObject("credentialSubject") {
                    put("id", "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp")
                    put("name", "Test User")
                    put("testClaim", "test value")
                }
            }
        }
        
        val response = client.post("/raw/jwt/sign") {
            setBody(signRequest)
        }
        response.expectSuccess()
        
        val signedJwt = response.body<String>()
        assertNotNull(signedJwt, "Signed JWT should not be null")
        signedJwt.expectLooksLikeJwt()
    }

    @Test
    fun shouldSignRawJwtWithMapping() = runTest {
        val client = environment.testHttpClient()
        
        val signRequest = buildJsonObject {
            put("issuerKey", issuerKey)
            put("issuerDid", JsonPrimitive(issuerDid))
            put("subjectDid", JsonPrimitive("did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"))
            putJsonObject("credentialData") {
                putJsonArray("@context") {
                    add(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))
                }
                putJsonArray("type") {
                    add(JsonPrimitive("VerifiableCredential"))
                    add(JsonPrimitive("UniversityDegreeCredential"))
                }
                putJsonObject("credentialSubject") {
                    put("id", "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp")
                    put("degree", "Bachelor of Science")
                    put("university", "Test University")
                }
            }
            putJsonObject("mapping") {
                putJsonObject("id") {
                    put("template", "\${issuerDid}#credential-\${uuid}")
                }
                putJsonObject("issuanceDate") {
                    put("template", "\${now}")
                }
                putJsonObject("expirationDate") {
                    put("template", "\${now + 365d}")
                }
            }
        }
        
        val response = client.post("/raw/jwt/sign") {
            setBody(signRequest)
        }
        response.expectSuccess()
        
        val signedJwt = response.body<String>()
        assertNotNull(signedJwt, "Signed JWT should not be null")
        signedJwt.expectLooksLikeJwt()
    }

    @Test
    fun shouldSignOpenBadgeCredential() = runTest {
        val client = environment.testHttpClient()
        val credentialData = loadJsonResource("issuance/openbadgecredential.json")
        
        val signRequest = buildJsonObject {
            put("issuerKey", issuerKey)
            put("issuerDid", issuerDid)
            put("subjectDid", "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp")
            put("credentialData", credentialData)
        }
        
        val response = client.post("/raw/jwt/sign") {
            setBody(signRequest)
        }
        response.expectSuccess()
        
        val signedJwt = response.body<String>()
        assertNotNull(signedJwt, "Signed JWT should not be null")
        signedJwt.expectLooksLikeJwt()
    }

    @Test
    fun shouldReturnJwtStartingWithEy() = runTest {
        val client = environment.testHttpClient()
        
        val signRequest = buildJsonObject {
            put("issuerKey", issuerKey)
            put("issuerDid", JsonPrimitive(issuerDid))
            put("subjectDid", JsonPrimitive("did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"))
            putJsonObject("credentialData") {
                putJsonArray("@context") {
                    add(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))
                }
                putJsonArray("type") {
                    add(JsonPrimitive("VerifiableCredential"))
                }
                putJsonObject("credentialSubject") {
                    put("id", "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp")
                }
            }
        }
        
        val response = client.post("/raw/jwt/sign") {
            setBody(signRequest)
        }
        response.expectSuccess()
        
        val signedJwt = response.body<String>()
        assertTrue(signedJwt.startsWith("ey"), "JWT should start with 'ey'")
    }
}
