package id.walt.issuer2.models

import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionStorageCodec
import id.walt.issuer2.repository.fixtures.PreBatchIssuanceSession
import id.walt.issuer2.testsupport.decodePublicSession
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.requests.notification.IssuedCredentialNotification
import id.walt.openid4vci.requests.notification.NotificationEvent
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Instant

class PublicIssuanceSessionTest {
    private val redactedIssuerKey = buildJsonObject { put("type", "redacted") }

    @Test
    fun `single session keeps the historical JSON shape with the key redacted`() {
        for (defaults in listOf(false, true)) for (nulls in listOf(false, true)) for (completed in listOf(false, true)) {
            val json = Json { encodeDefaults = defaults; explicitNulls = nulls }
            val baseline = baseline().copy(
                status = if (completed) IssuanceSessionStatus.SUCCESSFUL else IssuanceSessionStatus.ACTIVE,
                issuedCredentialFormat = "dc+sd-jwt".takeIf { completed },
            )
            val original = json.encodeToJsonElement(baseline)
            val session = IssuanceSessionStorageCodec.decode(original.toString())
            val expected = json.encodeToJsonElement(baseline.copy(issuerKey = redactedIssuerKey))
            assertEquals(expected, session.toPublicJson(json))
            assertEquals(expected, json.encodeToJsonElement(PublicIssuanceSession(session)))
            assertEquals(baseline.issuerKey, session.issuanceRequests.single().issuerKey)
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
        val managedKey = buildJsonObject {
            put("type", "tse")
            put("accessToken", "synthetic-token")
        }
        val requests = session.issuanceRequests + session.issuanceRequests.single().copy(
            credentialIdentifier = "B",
            issuerKey = managedKey,
        )
        val narrowed = session.copy(issuanceRequests = requests, authorizedCredentialIdentifiers = listOf("configuration"))
        val view = narrowed.toPublicJson()
        val publicRequests = assertNotNull(view["issuanceRequests"]).jsonArray
        assertEquals(2, publicRequests.size)
        publicRequests.forEach { assertEquals(redactedIssuerKey, it.jsonObject["issuerKey"]) }
        assertEquals(managedKey, narrowed.issuanceRequests.last().issuerKey)
        assertFalse(view.containsKey("profileId"))
        assertEquals(JsonArray(listOf(JsonPrimitive("configuration"))), view["authorizedCredentialIdentifiers"])
    }

    @Test
    fun `nested notifications round trip and retain flat single and batch public JSON`() {
        val legacy = Json.encodeToJsonElement(baseline().copy(issuedCredentialFormat = "dc+sd-jwt")).jsonObject
        val report = mapOf("walletNotificationId" to JsonPrimitive("notification"),
            "walletNotificationEvent" to JsonPrimitive("credential_accepted"),
            "walletNotificationEventDescription" to JsonPrimitive("stored"))
        val original = IssuanceSessionStorageCodec.decode(legacy.toString())
        val session = original.copy(issuanceResults = mapOf(
            "configuration" to original.issuanceResults.getValue("configuration").copy(
                walletNotification = IssuedCredentialNotification(
                    "configuration", "notification", NotificationEvent.CREDENTIAL_ACCEPTED, "stored",
                ),
            ),
        ))
        assertEquals(JsonObject(legacy + report), session.toPublicJson())
        assertEquals(session, IssuanceSessionStorageCodec.decode(Json.encodeToString(session)))
        val batch = session.copy(
            issuanceRequests = session.issuanceRequests + session.issuanceRequests.single().copy(credentialIdentifier = "B"),
            issuanceClaimId = "claim",
        )
        for (defaults in listOf(false, true)) for (nulls in listOf(false, true)) {
            val json = Json { encodeDefaults = defaults; explicitNulls = nulls }
            val internal = json.encodeToJsonElement(batch).jsonObject
            val result = internal.getValue("issuanceResults").jsonObject.getValue("configuration").jsonObject
            val publicResult = JsonObject(result - "walletNotification" + report)
            val publicBatch = JsonObject((internal - buildSet {
                add("deferredState")
                add("issuanceClaimId")
                if (batch.deferredStatus == null) add("deferredStatus")
            }) + ("issuanceResults" to buildJsonObject { put("configuration", publicResult) }))
            assertEquals(batch, IssuanceSessionStorageCodec.decode(internal.toString()))
            assertEquals(publicBatch, batch.toPublicJson(json))
            assertEquals(publicBatch, json.encodeToJsonElement(PublicIssuanceSession(batch)))
            assertEquals(
                session.issuanceResults.getValue("configuration").walletNotification,
                decodePublicSession(session.toPublicJson(json).toString())
                    .issuanceResults.getValue("configuration").walletNotification,
            )
            assertEquals(
                batch.issuanceResults.getValue("configuration").walletNotification,
                decodePublicSession(batch.toPublicJson(json).toString())
                    .issuanceResults.getValue("configuration").walletNotification,
            )
        }
    }

    private fun baseline() = PreBatchIssuanceSession(
        sessionId = "session", profileId = "profile", authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        credentialConfigurationId = "configuration", issuerKey = buildJsonObject { put("kid", "stored") },
        credentialData = buildJsonObject { put("name", "Alice") }, expiresAt = Instant.DISTANT_FUTURE,
    )
}
