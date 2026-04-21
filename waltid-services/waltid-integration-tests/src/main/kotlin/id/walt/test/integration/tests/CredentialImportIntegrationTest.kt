package id.walt.test.integration.tests

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.DidService
import id.walt.test.integration.environment.api.wallet.WalletApi
import io.klogging.Klogging
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@TestMethodOrder(OrderAnnotation::class)
class CredentialImportIntegrationTest : AbstractIntegrationTest(), Klogging {

    companion object {
        private lateinit var issuerKey: JWKKey
        private lateinit var issuerDid: String

        private suspend fun ensureIssuerInitialized() {
            if (::issuerKey.isInitialized) return
            issuerKey = JWKKey.generate(KeyType.Ed25519)
            val didResult = DidService.registerByKey("jwk", issuerKey)
            issuerDid = didResult.did
        }

        private suspend fun createSignedW3cJwtVc(
            subjectDid: String,
            credentialId: String = "${Uuid.random()}"
        ): String {
            ensureIssuerInitialized()
            val header = buildJsonObject {
                put("alg", "EdDSA")
                put("typ", "JWT")
                put("kid", "$issuerDid#0")
            }

            val payload = buildJsonObject {
                put("iss", issuerDid)
                put("sub", subjectDid)
                put("jti", credentialId)
                put("nbf", System.currentTimeMillis() / 1000 - 60)
                put("exp", System.currentTimeMillis() / 1000 + 3600)
                put("vc", buildJsonObject {
                    put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
                    put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
                    put("credentialSubject", buildJsonObject {
                        put("id", subjectDid)
                        put("name", "Test User")
                    })
                })
            }

            val headerBase64 = Json.encodeToString(header).encodeToByteArray().encodeToBase64Url()
            val payloadBase64 = Json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
            val signable = "$headerBase64.$payloadBase64"

            val signature = issuerKey.signRaw(signable.encodeToByteArray())
            val signatureBase64 = signature.encodeToBase64Url()

            
            println("jwt: $signable.$signatureBase64")
            return "$signable.$signatureBase64"
        }

        private suspend fun createSignedSdJwtVc(
            subjectDid: String,
            credentialId: String = "${Uuid.random()}"
        ): String {
            ensureIssuerInitialized()
            val header = buildJsonObject {
                put("alg", "EdDSA")
                put("typ", "vc+sd-jwt")
                put("kid", "$issuerDid#0")
            }

            val payload = buildJsonObject {
                put("iss", issuerDid)
                put("sub", subjectDid)
                put("jti", credentialId)
                put("nbf", System.currentTimeMillis() / 1000 - 60)
                put("exp", System.currentTimeMillis() / 1000 + 3600)
                put("vct", "https://example.com/credential/identity")
                put("_sd_alg", "sha-256")
                put("_sd", JsonArray(listOf(JsonPrimitive("abc123"))))
            }

            val headerBase64 = Json.encodeToString(header).encodeToByteArray().encodeToBase64Url()
            val payloadBase64 = Json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
            val signable = "$headerBase64.$payloadBase64"

            val signature = issuerKey.signRaw(signable.encodeToByteArray())
            val signatureBase64 = signature.encodeToBase64Url()

            val jwt = "$signable.$signatureBase64"

            // Add sample disclosures (base64 encoded arrays)
            val disclosure1 = """["salt1","given_name","John"]""".encodeToByteArray().encodeToBase64Url()
            val disclosure2 = """["salt2","family_name","Doe"]""".encodeToByteArray().encodeToBase64Url()

            return "$jwt~$disclosure1~$disclosure2~"
        }
    }

    @Order(1)
    @Test
    fun shouldImportW3cJwtCredential() = runTest {
        val did = defaultWalletApi.getDefaultDid().did
        val jwt = createSignedW3cJwtVc(did)

        val importedCredential = defaultWalletApi.importCredential(jwt, did)

        assertNotNull(importedCredential)
        assertEquals(jwt, importedCredential.document)
        // Verify it's in the list
        val credentials = defaultWalletApi.listCredentials()
        assertNotNull(credentials.find { it.id == importedCredential.id })
    }

    @Order(2)
    @Test
    fun shouldImportSdJwtCredential() = runTest {
        val did = defaultWalletApi.getDefaultDid().did
        val sdJwt = createSignedSdJwtVc(did)

        val importedCredential = defaultWalletApi.importCredential(sdJwt, did)

        assertNotNull(importedCredential)
        // For SD-JWT, the document in WalletCredential is only the JWT part
        val jwtPart = sdJwt.split("~").first()
        assertEquals(jwtPart, importedCredential.document)
        assertNotNull(importedCredential.disclosures)
        // Verify it's in the list
        val credentials = defaultWalletApi.listCredentials()
        assertNotNull(credentials.find { it.id == importedCredential.id })
    }
}
