package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.openapi.issuerapi.MdocDocs
import id.walt.mdoc.doc.MDoc
import id.walt.oid4vc.util.JwtUtils
import id.walt.test.integration.loadJsonResource
import id.walt.w3c.schemes.JwsSignatureScheme
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IssuanceCredentialStatusIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun issueJwtVcWithCredentialStatus() = runTest {
        val credentialStatusEntry = buildJsonObject {
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", "revocation")
            put("statusListCredential", "https://status.integration.example/lists/1")
            put("statusListIndex", "424242")
        }
        val req = IssuanceRequest(
            issuerKey = loadJsonResource("issuance/key.json"),
            issuerDid = loadResource("issuance/did.txt"),
            credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json",
            credentialData = loadJsonResource("issuance/openbadgecredential.json"),
            mapping = loadJsonResource("issuance/mapping.json"),
            credentialStatus = buildJsonArray { add(credentialStatusEntry) },
        )
        val offerUrl = issuerApi.issueJwtCredential(req)
        defaultWalletApi.resolveCredentialOffer(offerUrl)
        val claimed = defaultWalletApi.claimCredential(offerUrl)
        assertEquals(1, claimed.size)

        val jwtPayload = JwtUtils.parseJWTPayload(claimed.first().document)
        val vc = jwtPayload[JwsSignatureScheme.JwsOption.VC]?.jsonObject
            ?: error("JWT payload missing vc claim")
        val embedded = vc["credentialStatus"]
            ?: error("credentialStatus not embedded in issued VC")

        assertTrue(embedded is JsonArray, "credentialStatus must be a JSON array")
        assertEquals(1, embedded.jsonArray.size)
        val first = embedded.jsonArray.first().jsonObject
        assertEquals("BitstringStatusListEntry", first["type"]?.jsonPrimitive?.content)
        assertEquals(
            "https://status.integration.example/lists/1",
            first["statusListCredential"]?.jsonPrimitive?.content,
        )
        assertEquals("424242", first["statusListIndex"]?.jsonPrimitive?.content)
    }

    @Test
    fun issueSdJwtVcWithMergedStatusClaim() = runTest {
        val base = Json.decodeFromJsonElement(serializer<IssuanceRequest>(), loadJsonResource(
            "issuance/identity-credential-no-disclosures-no-mapping.json",
        ))
        val expectedUri = "https://status.integration.example/statuslists/99"
        val req = base.copy(
            sdJwtCredentialClaims = buildJsonObject {
                put(
                    "status",
                    buildJsonObject {
                        put("idx", JsonPrimitive(142))
                        put("uri", JsonPrimitive(expectedUri))
                    },
                )
            },
        )

        val offerUrl = issuerApi.issueSdJwtCredential(req)
        defaultWalletApi.resolveCredentialOffer(offerUrl)
        val claimed = defaultWalletApi.claimCredential(offerUrl)
        assertEquals(1, claimed.size)

        val sdJwtDocument = claimed.first().document
        val jwtPart = sdJwtDocument.substringBefore('~')
        val payload = JwtUtils.parseJWTPayload(jwtPart)

        val status = payload["status"]?.jsonObject ?: error("missing top-level status claim on SD-JWT VC")
        assertEquals(142L, status["idx"]?.jsonPrimitive?.longOrNull ?: status["idx"]?.jsonPrimitive?.intOrNull?.toLong())
        assertEquals(expectedUri, status["uri"]?.jsonPrimitive?.content)
    }

    @Test
    fun issueMdocWithStatusListInMso() = runTest {
        val expectedUri = "https://status.integration.example/statuslists/mdoc1"
        val req = MdocDocs.mdlBaseIssuanceExample.copy(
            mdocStatus = buildJsonObject {
                put(
                    "status_list",
                    buildJsonObject {
                        put("idx", JsonPrimitive(142))
                        put("uri", JsonPrimitive(expectedUri))
                    },
                )
            },
        )

        val offerUrl = issuerApi.issueMdocCredential(req)
        val mDocWallet = environment.getMdocWalletApi()
        val claimed = mDocWallet.claimCredential(offerUrl)
        assertEquals(1, claimed.size)

        val mDoc = MDoc.fromCBORHex(claimed.first().document)
        val statusList = assertNotNull(mDoc.MSO?.status?.statusList, "expected MSO.status.status_list")
        assertEquals(142u, statusList.index)
        assertEquals(expectedUri, statusList.uri)
    }

    @Test
    fun issueMdocWithIdentifierListInMso() = runTest {
        val expectedUri = "https://status.integration.example/identifierlists/2"
        val req = MdocDocs.mdlBaseIssuanceExample.copy(
            mdocStatus = buildJsonObject {
                put(
                    "identifier_list",
                    buildJsonObject {
                        put("id", JsonPrimitive("abcd"))
                        put("uri", JsonPrimitive(expectedUri))
                    },
                )
            },
        )

        val offerUrl = issuerApi.issueMdocCredential(req)
        val mDocWallet = environment.getMdocWalletApi()
        val claimed = mDocWallet.claimCredential(offerUrl)
        assertEquals(1, claimed.size)

        val mDoc = MDoc.fromCBORHex(claimed.first().document)
        val idList = assertNotNull(mDoc.MSO?.status?.identifierList, "expected MSO.status.identifier_list")
        assertContentEquals(byteArrayOf(0xab.toByte(), 0xcd.toByte()), idList.id)
        assertEquals(expectedUri, idList.uri)
    }
}
