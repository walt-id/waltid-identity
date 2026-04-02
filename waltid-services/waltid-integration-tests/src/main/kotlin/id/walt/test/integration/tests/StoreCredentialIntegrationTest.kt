@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.openapi.issuerapi.MdocDocs
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.util.JwtUtils
import id.walt.test.integration.expectLooksLikeJwt
import id.walt.test.integration.loadJsonResource
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.web.parameter.StoreCredentialRequest
import io.klogging.Klogging
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi

private const val testOrderCredentialIdErrorMessage = "Credential ID should be set - test order?"

private val jwtCredentialForIssue = IssuanceRequest(
    issuerKey = loadJsonResource("issuance/key.json"),
    issuerDid = loadResource("issuance/did.txt"),
    credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
    credentialData = loadJsonResource("issuance/openbadgecredential.json"),
    mapping = loadJsonResource("issuance/mapping.json")
)

@TestMethodOrder(OrderAnnotation::class)
class StoreCredentialIntegrationTest : AbstractIntegrationTest(), Klogging {
    companion object {
        var storedCredentialId: String? = null
        var storedJwtCredential: String? = null
        var issuedJwtCredential: String? = null
        var issuedMdocCredential: String? = null
    }

    @Order(0)
    @Test
    fun shouldIssueCredentialForStoreTest() = runTest {
        val offerUrl = issuerApi.issueJwtCredential(jwtCredentialForIssue)
        
        val claimedCredentials = defaultWalletApi.claimCredential(offerUrl)
        assertNotNull(claimedCredentials)
        assertEquals(1, claimedCredentials.size)
        
        issuedJwtCredential = claimedCredentials[0].document
        assertNotNull(issuedJwtCredential)
        
        defaultWalletApi.deleteCredential(claimedCredentials[0].id, permanent = true)
    }

    @Order(1)
    @Test
    fun shouldStoreJwtCredential() = runTest {
        assertNotNull(issuedJwtCredential, "JWT credential should be issued first")
        
        val request = StoreCredentialRequest(
            document = issuedJwtCredential!!,
            format = CredentialFormat.jwt_vc_json
        )
        
        val response = defaultWalletApi.storeCredential(request)
        
        assertEquals(HttpStatusCode.Created, response.status, "Should successfully store credential")
        
        val storedCredential = response.body<JsonObject>()
        assertNotNull(storedCredential)
        
        storedCredentialId = storedCredential["id"]?.jsonPrimitive?.content
        assertNotNull(storedCredentialId, "Stored credential should have an ID")
        
        storedJwtCredential = storedCredential["document"]?.jsonPrimitive?.content
        assertNotNull(storedJwtCredential)
        storedJwtCredential!!.expectLooksLikeJwt()
        
        val parsedDocument = storedCredential["parsedDocument"]?.jsonObject
        assertNotNull(parsedDocument, "Should have parsed document")
        
        val format = storedCredential["format"]?.jsonPrimitive?.content
        assertEquals(CredentialFormat.jwt_vc_json.value, format)
        
        val document = JwtUtils.parseJWTPayload(storedJwtCredential!!)
        assertContains(document.keys, JwsSignatureScheme.JwsOption.VC)
    }

    @Order(2)
    @Test
    fun shouldListStoredCredential() = runTest {
        assertNotNull(storedCredentialId, testOrderCredentialIdErrorMessage)
        
        val credentials = defaultWalletApi.listCredentials()
        assertTrue(
            credentials.any { it.id == storedCredentialId },
            "Stored credential should appear in list"
        )
    }

    @Order(3)
    @Test
    fun shouldGetStoredCredential() = runTest {
        assertNotNull(storedCredentialId, testOrderCredentialIdErrorMessage)
        
        val credential = defaultWalletApi.getCredential(storedCredentialId!!)
        assertNotNull(credential)
        assertEquals(storedCredentialId, credential.id)
        assertEquals(storedJwtCredential, credential.document)
    }

    @Order(4)
    @Test
    fun shouldRejectDuplicateCredential() = runTest {
        assertNotNull(storedJwtCredential, testOrderCredentialIdErrorMessage)
        
        val request = StoreCredentialRequest(
            document = storedJwtCredential!!,
            format = CredentialFormat.jwt_vc_json
        )
        
        val response = defaultWalletApi.storeCredential(request)
        
        assertEquals(
            HttpStatusCode.BadRequest,
            response.status,
            "Should reject duplicate credential"
        )
    }

    @Order(5)
    @Test
    fun shouldStoreJsonLdCredential() = runTest {
        val jsonLdCredential = """
        {
          "@context": [
            "https://www.w3.org/2018/credentials/v1",
            "https://www.w3.org/2018/credentials/examples/v1"
          ],
          "id": "http://example.edu/credentials/test-${System.currentTimeMillis()}",
          "type": ["VerifiableCredential", "UniversityDegreeCredential"],
          "issuer": "did:example:123",
          "issuanceDate": "2023-01-01T00:00:00Z",
          "credentialSubject": {
            "id": "did:example:456",
            "degree": {
              "type": "BachelorDegree",
              "name": "Bachelor of Science"
            }
          },
          "proof": {
            "type": "Ed25519Signature2020",
            "created": "2023-01-01T00:00:00Z",
            "verificationMethod": "did:example:123#key-1",
            "proofPurpose": "assertionMethod",
            "proofValue": "z58DAdFfa9SkqZMVPxAQpic7ndSayn1PzZs6ZjWp1CktyGesjuTSwRdoWhAfGFCF5bppETSTojQCrfFPP2oumHKtz"
          }
        }
        """.trimIndent()
        
        val request = StoreCredentialRequest(
            document = jsonLdCredential,
            format = CredentialFormat.ldp_vc
        )
        
        val response = defaultWalletApi.storeCredential(request)
        
        assertEquals(HttpStatusCode.Created, response.status, "Should successfully store JSON-LD credential")
        
        val storedCredential = response.body<JsonObject>()
        assertNotNull(storedCredential)
        
        val credentialId = storedCredential["id"]?.jsonPrimitive?.content
        assertNotNull(credentialId, "Stored JSON-LD credential should have an ID")
        
        val format = storedCredential["format"]?.jsonPrimitive?.content
        assertEquals(CredentialFormat.ldp_vc.value, format)
    }

    @Order(6)
    @Test
    fun shouldRejectInvalidJwtFormat() = runTest {
        val request = StoreCredentialRequest(
            document = "not.a.valid.jwt.at.all",
            format = CredentialFormat.jwt_vc_json
        )
        
        val response = defaultWalletApi.storeCredential(request)
        
        assertEquals(HttpStatusCode.BadRequest, response.status, "Should reject invalid JWT format")
    }

    @Order(7)
    @Test
    fun shouldDeleteStoredCredential() = runTest {
        assertNotNull(storedCredentialId, testOrderCredentialIdErrorMessage)
        
        defaultWalletApi.deleteCredential(storedCredentialId!!)
        
        val credential = defaultWalletApi.getCredential(storedCredentialId!!)
        assertNotNull(credential.deletedOn, "Credential should be marked as deleted")
    }

    @Order(8)
    @Test
    fun shouldRejectStoringDeletedCredential() = runTest {
        assertNotNull(storedJwtCredential, testOrderCredentialIdErrorMessage)
        
        val request = StoreCredentialRequest(
            document = storedJwtCredential!!,
            format = CredentialFormat.jwt_vc_json
        )
        
        val response = defaultWalletApi.storeCredential(request)
        
        assertEquals(
            HttpStatusCode.BadRequest,
            response.status,
            "Should reject storing credential that is soft-deleted"
        )
    }

    @Order(9)
    @Test
    fun shouldAllowStoringAfterPermanentDeletion() = runTest {
        assertNotNull(storedCredentialId, testOrderCredentialIdErrorMessage)
        
        defaultWalletApi.deleteCredential(storedCredentialId!!, permanent = true)
        
        assertNotNull(storedJwtCredential)
        val request = StoreCredentialRequest(
            document = storedJwtCredential!!,
            format = CredentialFormat.jwt_vc_json
        )
        
        val response = defaultWalletApi.storeCredential(request)
        
        assertEquals(
            HttpStatusCode.Created,
            response.status,
            "Should allow storing credential after permanent deletion"
        )
    }

    @Order(10)
    @Test
    fun shouldVerifyStoredCredential() = runTest {
        assertNotNull(storedCredentialId, testOrderCredentialIdErrorMessage)
        
        val credentials = defaultWalletApi.listCredentials()
        val credential = credentials.find { it.id == storedCredentialId }
        
        assertNotNull(credential, "Stored credential should exist")
        assertNull(credential.deletedOn, "Credential should not be deleted")
        assertFalse(credential.pending, "Credential should not be pending")
    }

    @Order(11)
    @Test
    fun shouldIssueMdocCredentialForStoreTest() = runTest {
        val mDLIssuanceRequest = MdocDocs.mdlBaseIssuanceExample
        val offerUrl = issuerApi.issueMdocCredential(mDLIssuanceRequest)
        
        val mdocWallet = environment.getMdocWalletApi()
        val claimedCredentials = mdocWallet.claimCredential(offerUrl)
        assertNotNull(claimedCredentials)
        assertEquals(1, claimedCredentials.size)
        
        val mdocCredential = claimedCredentials[0]
        assertEquals(CredentialFormat.mso_mdoc, mdocCredential.format)
        
        issuedMdocCredential = mdocCredential.document
        assertNotNull(issuedMdocCredential)
        
        mdocWallet.deleteCredential(mdocCredential.id, permanent = true)
    }

    @Order(12)
    @Test
    fun shouldStoreMdocCredential() = runTest {
        assertNotNull(issuedMdocCredential, "mDoc credential should be issued first")
        
        val request = StoreCredentialRequest(
            document = issuedMdocCredential!!,
            format = CredentialFormat.mso_mdoc
        )
        
        val response = defaultWalletApi.storeCredential(request)
        
        assertEquals(HttpStatusCode.Created, response.status, "Should successfully store mDoc credential")
        
        val storedCredential = response.body<JsonObject>()
        assertNotNull(storedCredential)
        
        val mdocCredentialId = storedCredential["id"]?.jsonPrimitive?.content
        assertNotNull(mdocCredentialId, "Stored mDoc credential should have an ID")
        assertTrue(mdocCredentialId.startsWith("urn:uuid:"), "mDoc credential ID should be a generated UUID")
        
        val format = storedCredential["format"]?.jsonPrimitive?.content
        assertEquals(CredentialFormat.mso_mdoc.value, format)
        
        val document = storedCredential["document"]?.jsonPrimitive?.content
        assertEquals(issuedMdocCredential, document)
        
        defaultWalletApi.deleteCredential(mdocCredentialId, permanent = true)
    }
}
