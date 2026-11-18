package id.walt.issuer2.models

import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionStorageCodec
import id.walt.issuer2.repository.fixtures.PreBatchIssuanceSession
import id.walt.openid4vci.offers.AuthenticationMethod
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Instant

class PublicIssuanceSessionTest {
    @Test
    fun preallocatedInventoryAndPreparedResponsesArePrivateInBothSessionViews() {
        val original = IssuanceSessionStorageCodec.decode(Json.encodeToString(baseline()))
        val request = original.issuanceRequests.single().copy(
            credentialStatus = JsonArray(listOf(JsonArray(listOf(buildJsonObject { put("unused", "private-status") })))),
        )
        val state = id.walt.issuer2.domain.CredentialStatusIssuanceState(
            leaseId = "private-lease",
            operations = mapOf("configuration" to id.walt.issuer2.domain.CredentialStatusIssuanceOperation(
                "private-operation", listOf("private-holder"), 1,
                id.walt.openid4vci.responses.credential.CredentialResponse(listOf(
                    id.walt.openid4vci.responses.credential.IssuedCredential(JsonPrimitive("private-signed-response")),
                )),
            )),
        )
        for (requests in listOf(listOf(request), listOf(request, request.copy(credentialIdentifier = "B")))) {
            val session = original.copy(issuanceRequests = requests, credentialStatusIssuance = state)
            assertTrue(Json.encodeToString(session).contains("private-signed-response"))
            val public = session.toPublicJson().toString()
            assertFalse(public.contains("private-"))
            assertFalse(public.contains("credentialStatusIssuance"))
        }
        assertTrue((0 until singleIssuanceSessionDescriptor.elementsCount).none {
            singleIssuanceSessionDescriptor.getElementName(it) == "credentialStatusIssuance"
        })
        assertTrue((0 until multiIssuanceSessionDescriptor.elementsCount).none {
            multiIssuanceSessionDescriptor.getElementName(it) == "credentialStatusIssuance"
        })
    }

    @Test
    fun `single session JSON equals the baseline serializer with every optional-field mode`() {
        for (defaults in listOf(false, true)) for (nulls in listOf(false, true)) for (completed in listOf(false, true)) {
            val json = Json { encodeDefaults = defaults; explicitNulls = nulls }
            val baseline = baseline().copy(
                status = if (completed) IssuanceSessionStatus.SUCCESSFUL else IssuanceSessionStatus.ACTIVE,
                issuedCredentialFormat = "dc+sd-jwt".takeIf { completed },
            )
            val original = json.encodeToJsonElement(baseline)
            val session = IssuanceSessionStorageCodec.decode(original.toString())
            assertEquals(original, session.toPublicJson(json))
            assertEquals(original, json.encodeToJsonElement(PublicIssuanceSession(session)))
        }
    }

    @Test
    fun `proof copies preserve order and duplicates and original multiple selections stay plural after narrowing`() {
        val session = IssuanceSessionStorageCodec.decode(Json.encodeToString(baseline()))
        val keyA = buildJsonObject { put("kid", "A") }
        val keyB = buildJsonObject { put("kid", "B") }
        for (keys in listOf(listOf(keyA), listOf(keyA, keyB), listOf(keyA, keyA))) {
            val proofBound = session.copy(issuanceRequests = listOf(session.issuanceRequests.single().copy(expectedCredentialProofKeyJwks = keys)))
            val view = proofBound.toPublicJson()
            assertFalse(view.containsKey("issuanceRequests"))
            if (keys.size == 1) assertEquals(keyA, view["expectedCredentialProofKeyJwk"])
            else {
                assertFalse(view.containsKey("expectedCredentialProofKeyJwk"))
                assertEquals(JsonArray(keys), view["expectedCredentialProofKeyJwks"])
            }
        }
        val requests = session.issuanceRequests + session.issuanceRequests.single().copy(credentialIdentifier = "B")
        val narrowed = session.copy(issuanceRequests = requests, authorizedCredentialIdentifiers = listOf("configuration"))
        val view = narrowed.toPublicJson()
        assertEquals(2, assertNotNull(view["issuanceRequests"]).jsonArray.size)
        assertFalse(view.containsKey("profileId"))
        assertEquals(JsonArray(listOf(JsonPrimitive("configuration"))), view["authorizedCredentialIdentifiers"])
    }

    private fun baseline() = PreBatchIssuanceSession(
        sessionId = "session", profileId = "profile", authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        credentialConfigurationId = "configuration", issuerKey = buildJsonObject { put("kid", "stored") },
        credentialData = buildJsonObject { put("name", "Alice") }, expiresAt = Instant.DISTANT_FUTURE,
    )
}
