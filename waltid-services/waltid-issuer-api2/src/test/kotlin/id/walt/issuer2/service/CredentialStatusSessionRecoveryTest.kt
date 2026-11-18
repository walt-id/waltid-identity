package id.walt.issuer2.service

import id.walt.commons.persistence.InMemoryPersistence
import id.walt.issuer2.domain.*
import id.walt.issuer2.repository.*
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class CredentialStatusSessionRecoveryTest {
    private var now = Clock.System.now()
    private val repository = ConfiguredIssuanceSessionRepository(
        InMemoryPersistence("status-session-recovery", 1.days),
        InMemoryPersistence("status-session-recovery-keys", 1.days),
    )
    private fun service() = IssuanceSessionService(repository, now = { now })
    private suspend fun create() = service().createSession(IssuanceSession(
        sessionId = "session", authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        issuanceRequests = listOf(IssuanceRequest(
            credentialIdentifier = "credential", profileId = "profile", credentialConfigurationId = "configuration",
            issuerKey = buildJsonObject { put("type", "test-backend") }, credentialData = buildJsonObject {},
        )), expiresAt = now + 1.days,
    ))

    @Test
    fun claimAndPreparedResponseSurviveServiceRecreationAndRelease() = runTest {
        create()
        val first = assertNotNull(service().claimCredentialStatusSession("session"))
        assertNotNull(repository.get("session"), "A claim must not remove the session")
        assertNull(service().claimCredentialStatusSession("session"))
        val operation = service().prepareCredentialStatusOperation(first, "credential", listOf("key1", "key2"), 2)
        val response = CredentialResponse(listOf(IssuedCredential(JsonPrimitive("signed-once"))))
        service().checkpointCredentialStatusResponse(first, "credential", operation.id, response)
        service().releaseCredentialStatusSession(first)
        val retried = assertNotNull(service().claimCredentialStatusSession("session"))
        val recovered = service().prepareCredentialStatusOperation(retried, "credential", listOf("key1", "key2"), 2)
        assertEquals(operation.id, recovered.id)
        assertEquals(response, recovered.response)
        val completed = service().finishCredentialStatusSession(retried.copy(
            issuanceResults = mapOf("credential" to IssuanceResult("dc+sd-jwt")),
        ), "credential")
        assertTrue(completed.credentialStatusIssuance!!.operations.isEmpty())
        assertNull(completed.credentialStatusIssuance.leaseId)
        val next = assertNotNull(service().claimCredentialStatusSession("session"))
        assertNotEquals(operation.id,
            service().prepareCredentialStatusOperation(next, "credential", listOf("new-key"), 1).id)
    }

    @Test
    fun staleWorkersCannotCheckpointOrReleaseANewerClaim() = runTest {
        create()
        val stale = assertNotNull(service().claimCredentialStatusSession("session"))
        val operation = service().prepareCredentialStatusOperation(stale, "credential", listOf("key"), 1)
        now += 6.minutes
        val fresh = assertNotNull(service().claimCredentialStatusSession("session"))
        assertFailsWith<CredentialStatusOperationException> {
            service().checkpointCredentialStatusResponse(stale, "credential", operation.id, CredentialResponse())
        }
        assertFailsWith<CredentialStatusOperationException> { service().releaseCredentialStatusSession(stale) }
        assertEquals(fresh.credentialStatusIssuance!!.leaseId,
            repository.get("session")!!.credentialStatusIssuance!!.leaseId)
        assertEquals(operation.id,
            service().prepareCredentialStatusOperation(fresh, "credential", listOf("key"), 1).id)
    }

    @Test
    fun ordinarySessionUpdatesPreservePrivateOperationsAndDoNotReleaseTheLease() = runTest {
        create()
        val claim = assertNotNull(service().claimCredentialStatusSession("session"))
        val operation = service().prepareCredentialStatusOperation(claim, "credential", listOf("key"), 1)
        service().saveSession(claim.copy(statusReason = "updated separately"))
        val saved = repository.get("session")!!
        assertEquals(claim.credentialStatusIssuance!!.leaseId, saved.credentialStatusIssuance!!.leaseId)
        assertEquals(operation, saved.credentialStatusIssuance.operations["credential"])
        assertFailsWith<CredentialStatusOperationException> {
            service().prepareCredentialStatusOperation(claim, "credential", listOf("other-key"), 1)
        }
    }
}
