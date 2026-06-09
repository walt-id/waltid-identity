package id.walt.issuer2.repository

import id.walt.issuer2.domain.IssuanceSession
import id.walt.openid4vci.offers.AuthenticationMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ConfiguredIssuanceSessionRepositoryTest {

    @Test
    fun saveGetListAndRemoveSession() = runTest {
        val repository = ConfiguredIssuanceSessionRepository()
        val session = testSession()

        try {
            repository.save(session)

            assertEquals(session, repository.get(session.sessionId))
            assertTrue(repository.list().any { it.sessionId == session.sessionId })

            repository.remove(session.sessionId)

            assertNull(repository.get(session.sessionId))
        } finally {
            repository.remove(session.sessionId)
        }
    }

    private fun testSession() = IssuanceSession(
        sessionId = "session-${Clock.System.now().toEpochMilliseconds()}",
        profileId = "profile-id",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        credentialConfigurationId = "identity_credential",
        issuerKey = buildJsonObject {
            put("type", "jwk")
        },
        credentialData = buildJsonObject {
            put("given_name", "Jane")
            put("family_name", "Doe")
        },
        issuerDid = "did:web:issuer.example",
        expiresAt = Clock.System.now().plus(5.minutes),
    )
}
