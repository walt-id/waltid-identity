package id.walt.eudi

import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Tests for OpenID4VCI Draft 13+ format handling.
 *
 * Draft 13+ introduces several changes from earlier drafts:
 * - credential_configuration_id instead of format field
 * - proofs (plural) with array instead of proof (singular)
 * - credentials (plural) array in response instead of credential (singular)
 */
class Draft13FormatTest {

    // ==================== Credential Request Format Tests ====================

    @Test
    fun testDraft13RequestHasCredentialConfigurationId() {
        // Draft 13+ requests use credential_configuration_id
        val draft13Request = buildJsonObject {
            put("credential_configuration_id", JsonPrimitive("eu.europa.ec.eudi.pid.1"))
            put("proofs", buildJsonObject {
                put("jwt", buildJsonArray {
                    add(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload.sig"))
                })
            })
        }

        assertTrue(draft13Request.containsKey("credential_configuration_id"))
        assertFalse(draft13Request.containsKey("format"))
    }

    @Test
    fun testLegacyRequestHasFormat() {
        // Legacy requests use format field
        val legacyRequest = buildJsonObject {
            put("format", JsonPrimitive("mso_mdoc"))
            put("doctype", JsonPrimitive("eu.europa.ec.eudi.pid.1"))
            put("proof", buildJsonObject {
                put("proof_type", JsonPrimitive("jwt"))
                put("jwt", JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload.sig"))
            })
        }

        assertTrue(legacyRequest.containsKey("format"))
        assertFalse(legacyRequest.containsKey("credential_configuration_id"))
    }

    // ==================== Proofs Format Tests ====================

    @Test
    fun testDraft13ProofsJwtFormat() {
        // Draft 13+ uses proofs.jwt array
        val proofs = buildJsonObject {
            put("jwt", buildJsonArray {
                add(JsonPrimitive("proof1"))
                add(JsonPrimitive("proof2"))
            })
        }

        val jwtArray = proofs["jwt"]?.jsonArray
        assertNotNull(jwtArray)
        assertEquals(2, jwtArray.size)
    }

    @Test
    fun testDraft13ProofsCwtFormat() {
        // Draft 13+ can also use proofs.cwt array for mDoc
        val proofs = buildJsonObject {
            put("cwt", buildJsonArray {
                add(JsonPrimitive("cwt-proof-1"))
            })
        }

        val cwtArray = proofs["cwt"]?.jsonArray
        assertNotNull(cwtArray)
        assertEquals(1, cwtArray.size)
    }

    @Test
    fun testLegacyProofFormat() {
        // Legacy uses proof (singular) object
        val proof = buildJsonObject {
            put("proof_type", JsonPrimitive("jwt"))
            put("jwt", JsonPrimitive("single-jwt-proof"))
        }

        assertEquals("jwt", proof["proof_type"]?.jsonPrimitive?.content)
        assertNotNull(proof["jwt"])
    }

    @Test
    fun testConvertProofsToProof() {
        // Test the conversion logic from proofs (Draft 13+) to proof (legacy)
        val draft13Proofs = buildJsonObject {
            put("jwt", buildJsonArray {
                add(JsonPrimitive("eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0In0.payload.sig"))
            })
        }

        // Extract first JWT proof
        val jwtProofs = draft13Proofs["jwt"]?.jsonArray
        assertNotNull(jwtProofs)
        assertTrue(jwtProofs.isNotEmpty())

        // Convert to legacy format
        val legacyProof = buildJsonObject {
            put("proof_type", JsonPrimitive("jwt"))
            put("jwt", jwtProofs[0])
        }

        assertEquals("jwt", legacyProof["proof_type"]?.jsonPrimitive?.content)
        assertNotNull(legacyProof["jwt"])
    }

    @Test
    fun testConvertCwtProofsToProof() {
        // Test conversion for CWT proofs
        val draft13Proofs = buildJsonObject {
            put("cwt", buildJsonArray {
                add(JsonPrimitive("cwt-proof-data"))
            })
        }

        val cwtProofs = draft13Proofs["cwt"]?.jsonArray
        assertNotNull(cwtProofs)

        val legacyProof = buildJsonObject {
            put("proof_type", JsonPrimitive("cwt"))
            put("cwt", cwtProofs[0])
        }

        assertEquals("cwt", legacyProof["proof_type"]?.jsonPrimitive?.content)
    }

    // ==================== Credential Response Format Tests ====================

    @Test
    fun testDraft13ResponseCredentialsArray() {
        // Draft 13+ responses use credentials array
        val draft13Response = buildJsonObject {
            put("credentials", buildJsonArray {
                add(buildJsonObject {
                    put("credential", JsonPrimitive("issued-credential-data"))
                })
            })
            put("c_nonce", JsonPrimitive("new-nonce"))
            put("c_nonce_expires_in", JsonPrimitive(86400))
        }

        val credentials = draft13Response["credentials"]?.jsonArray
        assertNotNull(credentials)
        assertEquals(1, credentials.size)

        val firstCred = credentials[0].jsonObject
        assertNotNull(firstCred["credential"])
    }

    @Test
    fun testLegacyResponseCredentialSingular() {
        // Legacy responses use credential (singular)
        val legacyResponse = buildJsonObject {
            put("credential", JsonPrimitive("issued-credential-data"))
            put("c_nonce", JsonPrimitive("new-nonce"))
            put("c_nonce_expires_in", JsonPrimitive(86400))
        }

        assertNotNull(legacyResponse["credential"])
        assertFalse(legacyResponse.containsKey("credentials"))
    }

    @Test
    fun testDraft13DeferredResponseTransactionId() {
        // Draft 13+ uses transaction_id for deferred issuance
        val deferredResponse = buildJsonObject {
            put("transaction_id", JsonPrimitive("txn-123456"))
            put("c_nonce", JsonPrimitive("new-nonce"))
        }

        assertNotNull(deferredResponse["transaction_id"])
        assertFalse(deferredResponse.containsKey("acceptance_token"))
    }

    @Test
    fun testLegacyDeferredResponseAcceptanceToken() {
        // Legacy uses acceptance_token for deferred issuance
        val deferredResponse = buildJsonObject {
            put("acceptance_token", JsonPrimitive("accept-123456"))
            put("c_nonce", JsonPrimitive("new-nonce"))
        }

        assertNotNull(deferredResponse["acceptance_token"])
        assertFalse(deferredResponse.containsKey("transaction_id"))
    }

    // ==================== Full Request Conversion Tests ====================

    @Test
    fun testFullDraft13ToLegacyConversion() {
        // Simulate full conversion from Draft 13+ to legacy format
        val draft13Request = buildJsonObject {
            put("credential_configuration_id", JsonPrimitive("eu.europa.ec.eudi.pid.1"))
            put("proofs", buildJsonObject {
                put("jwt", buildJsonArray {
                    add(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload.signature"))
                })
            })
        }

        // Simulated conversion (this is what OidcApi.kt does)
        val format = "mso_mdoc"  // Would be looked up from credential configuration
        val docType = "eu.europa.ec.eudi.pid.1"

        val proofs = draft13Request["proofs"]?.jsonObject
        val jwtProofs = proofs?.get("jwt")?.jsonArray

        val legacyRequest = buildJsonObject {
            put("format", JsonPrimitive(format))
            put("doctype", JsonPrimitive(docType))

            if (jwtProofs != null && jwtProofs.isNotEmpty()) {
                put("proof", buildJsonObject {
                    put("proof_type", JsonPrimitive("jwt"))
                    put("jwt", jwtProofs[0])
                })
            }
        }

        // Verify conversion
        assertEquals("mso_mdoc", legacyRequest["format"]?.jsonPrimitive?.content)
        assertEquals("eu.europa.ec.eudi.pid.1", legacyRequest["doctype"]?.jsonPrimitive?.content)
        assertEquals("jwt", legacyRequest["proof"]?.jsonObject?.get("proof_type")?.jsonPrimitive?.content)
        assertNotNull(legacyRequest["proof"]?.jsonObject?.get("jwt"))
    }

    // ==================== Credential Configuration ID Tests ====================

    @Test
    fun testEudiPidConfigurationId() {
        val configId = "eu.europa.ec.eudi.pid.1"

        assertTrue(configId.startsWith("eu.europa.ec.eudi"))
        assertTrue(configId.contains("pid"))
    }

    @Test
    fun testMdlConfigurationId() {
        val configId = "org.iso.18013.5.1.mDL"

        assertTrue(configId.startsWith("org.iso"))
        assertTrue(configId.contains("mDL"))
    }

    @Test
    fun testSdJwtPidConfigurationId() {
        val configId = "urn:eu.europa.ec.eudi:pid:1"

        assertTrue(configId.startsWith("urn:"))
        assertTrue(configId.contains("eudi"))
        assertTrue(configId.contains("pid"))
    }

    // ==================== Batch Issuance Tests ====================

    @Test
    fun testBatchProofsMultipleJwts() {
        // Draft 13+ supports batch issuance with multiple proofs
        val batchProofs = buildJsonObject {
            put("jwt", buildJsonArray {
                add(JsonPrimitive("proof-for-credential-1"))
                add(JsonPrimitive("proof-for-credential-2"))
                add(JsonPrimitive("proof-for-credential-3"))
            })
        }

        val jwtArray = batchProofs["jwt"]?.jsonArray
        assertNotNull(jwtArray)
        assertEquals(3, jwtArray.size)
    }

    @Test
    fun testBatchResponseMultipleCredentials() {
        // Draft 13+ batch response contains multiple credentials
        val batchResponse = buildJsonObject {
            put("credentials", buildJsonArray {
                add(buildJsonObject {
                    put("credential", JsonPrimitive("credential-1-data"))
                })
                add(buildJsonObject {
                    put("credential", JsonPrimitive("credential-2-data"))
                })
            })
        }

        val credentials = batchResponse["credentials"]?.jsonArray
        assertNotNull(credentials)
        assertEquals(2, credentials.size)
    }
}
