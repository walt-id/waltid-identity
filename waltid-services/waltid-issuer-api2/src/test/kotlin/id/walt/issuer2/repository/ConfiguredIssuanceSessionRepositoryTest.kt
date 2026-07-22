package id.walt.issuer2.repository

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.domain.IssuanceSession
import id.walt.openid4vci.offers.AuthenticationMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ConfiguredIssuanceSessionRepositoryTest {

    @Test
    fun saveGetListAndRemoveSession() = runTest {
        val sessionPersistence = ConfiguredPersistence(
            "issuer2-test-sessions-${Clock.System.now().toEpochMilliseconds()}",
            defaultExpiration = 5.minutes,
            encoding = { Json.encodeToString(IssuanceSession.serializer(), it) },
            decoding = { Json.decodeFromString(IssuanceSession.serializer(), it) },
        )
        val sidecarPersistence = ConfiguredPersistence(
            "issuer2-test-sidecars-${Clock.System.now().toEpochMilliseconds()}",
            defaultExpiration = 5.minutes,
            encoding = { it },
            decoding = { it },
        )
        val repository = ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence)
        val session = testSession()

        try {
            val saved = repository.save(session)

            assertNotNull(saved.crypto2IssuerStoredKey)
            assertEquals(saved, ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence).get(session.sessionId))
            assertTrue(repository.list().any { it.sessionId == session.sessionId })
            assertFalse(Json.encodeToString(IssuanceSession.serializer(), saved).contains("crypto2IssuerStoredKey"))
            assertFails { repository.save(session.copy(crypto2IssuerStoredKey = "not-a-stored-key")) }

            val replacement = session.copy(
                issuerKey = KeySerialization.serializeKeyToJson(JWKKey.generate(KeyType.secp256r1)).jsonObject,
                crypto2IssuerStoredKey = null,
            )
            sessionPersistence.set(session.sessionId, replacement, 5.minutes)
            val repaired = assertNotNull(
                ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence).get(session.sessionId)
            )
            assertNotEquals(saved.crypto2IssuerStoredKey, repaired.crypto2IssuerStoredKey)

            sidecarPersistence.remove(session.sessionId)
            assertNotNull(
                ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence)
                    .get(session.sessionId)?.crypto2IssuerStoredKey
            )

            sidecarPersistence.set(session.sessionId, "not-a-stored-key", 5.minutes)
            assertFails { ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence).get(session.sessionId) }

            repository.remove(session.sessionId)

            assertNull(repository.get(session.sessionId))
        } finally {
            repository.remove(session.sessionId)
        }
    }

    private suspend fun testSession() = IssuanceSession(
        sessionId = "session-${Clock.System.now().toEpochMilliseconds()}",
        profileId = "profile-id",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        credentialConfigurationId = "identity_credential",
        issuerKey = KeySerialization.serializeKeyToJson(JWKKey.generate(KeyType.secp256r1)).jsonObject,
        credentialData = buildJsonObject {
            put("given_name", "Jane")
            put("family_name", "Doe")
        },
        issuerDid = "did:web:issuer.example",
        expiresAt = Clock.System.now().plus(5.minutes),
    )

}
