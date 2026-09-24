package id.walt.issuer2.repository

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.commons.persistence.Persistence
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceRequest
import id.walt.issuer2.repository.fixtures.PreBatchIssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.openid4vci.offers.AuthenticationMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlin.time.Instant
import kotlin.time.Duration

class ConfiguredIssuanceSessionRepositoryTest {

    @Test
    fun readsPreBatchSnapshotsAndSingleKeySidecarsThroughGetListAndTake() = runTest {
        val key = KeySerialization.serializeKeyToJson(JWKKey.generate(KeyType.secp256r1)).jsonObject
        for (completed in listOf(false, true)) {
            val namespace = "issuer2-legacy-${java.util.UUID.randomUUID()}"
            val raw = ConfiguredPersistence<String>(namespace, 5.minutes, { it }, { it })
            val sessions = SerializedSessions(raw)
            val keys = ConfiguredPersistence<String>("$namespace-keys", 5.minutes, { it }, { it })
            val repository = ConfiguredIssuanceSessionRepository(sessions, keys)
            val legacy = PreBatchIssuanceSession(
                sessionId = "old-session",
                profileId = "saved-profile",
                authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
                credentialConfigurationId = "saved-configuration",
                issuerKey = key,
                credentialData = buildJsonObject { put("given_name", "Saved snapshot") },
                expiresAt = Instant.DISTANT_FUTURE,
                status = if (completed) IssuanceSessionStatus.SUCCESSFUL else IssuanceSessionStatus.ACTIVE,
                issuedCredentialFormat = "dc+sd-jwt".takeIf { completed },
            )
            val encoded = Json.encodeToString(PreBatchIssuanceSession.serializer(), legacy)
            val converted = IssuanceSessionStorageCodec.decode(encoded)
            val sidecar = assertNotNull(IssuanceSessionCrypto2Keys.migrateLegacyKey(converted.issuanceRequests.single()))
            try {
                raw.set(legacy.sessionId, encoded, 5.minutes)
                keys.set(legacy.sessionId, sidecar, 5.minutes)
                val loaded = assertNotNull(repository.get(legacy.sessionId))
                assertEquals("saved-configuration", loaded.issuanceRequests.single().credentialIdentifier)
                assertEquals(legacy.credentialData, loaded.issuanceRequests.single().credentialData)
                assertEquals(legacy.issuerKey, loaded.issuanceRequests.single().issuerKey)
                assertEquals(sidecar, loaded.issuanceRequests.single().crypto2IssuerStoredKey)
                assertNull(loaded.authorizedCredentialIdentifiers)
                assertEquals(if (completed) setOf("saved-configuration") else emptySet(), loaded.issuanceResults.keys)
                assertEquals(legacy.expiresAt, loaded.expiresAt)
                assertFalse(loaded.isClosed)
                assertEquals(listOf(loaded), repository.list())
                assertEquals(setOf("saved-configuration"), Json.parseToJsonElement(assertNotNull(keys[legacy.sessionId])).jsonObject.keys)
                // A claim must also accept the legacy sidecar, without backfilling a removed session.
                keys.set(legacy.sessionId, sidecar, 5.minutes)
                assertEquals(loaded, repository.take(legacy.sessionId))
                assertNull(keys[legacy.sessionId])
                assertNull(repository.get(legacy.sessionId))
                repository.save(loaded)
                val stored = Json.parseToJsonElement(assertNotNull(raw[legacy.sessionId])).jsonObject
                assertTrue("issuanceRequests" in stored)
                assertFalse("profileId" in stored)
                assertFalse(stored.toString().contains("issuedAt"))
            } finally {
                repository.remove(legacy.sessionId)
            }
        }
    }

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

            assertNotNull(saved.issuanceRequests.single().crypto2IssuerStoredKey)
            assertEquals(saved, ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence).get(session.sessionId))
            assertTrue(repository.list().any { it.sessionId == session.sessionId })
            assertFalse(Json.encodeToString(IssuanceSession.serializer(), saved).contains("crypto2IssuerStoredKey"))
            assertFails {
                repository.save(
                    session.copy(issuanceRequests = session.issuanceRequests.map {
                        it.copy(crypto2IssuerStoredKey = "not-a-stored-key")
                    })
                )
            }

            val replacement = session.copy(issuanceRequests = session.issuanceRequests.map {
                it.copy(
                    issuerKey = KeySerialization.serializeKeyToJson(JWKKey.generate(KeyType.secp256r1)).jsonObject,
                    crypto2IssuerStoredKey = null,
                )
            })
            sessionPersistence.set(session.sessionId, replacement, 5.minutes)
            val repaired = assertNotNull(
                ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence).get(session.sessionId)
            )
            assertNotEquals(
                saved.issuanceRequests.single().crypto2IssuerStoredKey,
                repaired.issuanceRequests.single().crypto2IssuerStoredKey,
            )

            sidecarPersistence.remove(session.sessionId)
            assertNotNull(
                ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence)
                    .get(session.sessionId)?.issuanceRequests?.single()?.crypto2IssuerStoredKey
            )

            sidecarPersistence.set(session.sessionId, "not-a-stored-key", 5.minutes)
            assertFails { ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence).get(session.sessionId) }

            repository.remove(session.sessionId)

            assertNull(repository.get(session.sessionId))
        } finally {
            repository.remove(session.sessionId)
        }
    }

    @Test
    fun staleSidecarIsRepairedWithoutReplacingValidSiblingKeys() = runTest {
        val suffix = Clock.System.now().toEpochMilliseconds()
        val sessionPersistence = ConfiguredPersistence(
            "issuer2-test-multi-sessions-$suffix",
            defaultExpiration = 5.minutes,
            encoding = { Json.encodeToString(IssuanceSession.serializer(), it) },
            decoding = { Json.decodeFromString(IssuanceSession.serializer(), it) },
        )
        val sidecarPersistence = ConfiguredPersistence(
            "issuer2-test-multi-sidecars-$suffix",
            defaultExpiration = 5.minutes,
            encoding = { it },
            decoding = { it },
        )
        val repository = ConfiguredIssuanceSessionRepository(sessionPersistence, sidecarPersistence)
        val baseSession = testSession()
        val firstRequest = baseSession.issuanceRequests.single()
        val session = baseSession.copy(
            issuanceRequests = listOf(
                firstRequest,
                firstRequest.copy(
                    credentialIdentifier = "credential-2",
                    issuerKey = KeySerialization.serializeKeyToJson(
                        JWKKey.generate(KeyType.secp256r1)
                    ).jsonObject,
                ),
            ),
        )

        try {
            val saved = repository.save(session)
            val savedSidecars = saved.issuanceRequests.associate { request ->
                request.credentialIdentifier to assertNotNull(request.crypto2IssuerStoredKey)
            }
            val replacement = session.copy(
                issuanceRequests = session.issuanceRequests.map { request ->
                    if (request.credentialIdentifier == "credential-2") {
                        request.copy(
                            issuerKey = KeySerialization.serializeKeyToJson(
                                JWKKey.generate(KeyType.secp256r1)
                            ).jsonObject,
                            crypto2IssuerStoredKey = null,
                        )
                    } else {
                        request
                    }
                },
            )
            sessionPersistence.set(session.sessionId, replacement, 5.minutes)

            val repaired = assertNotNull(repository.get(session.sessionId))
            val repairedSidecars = repaired.issuanceRequests.associate { request ->
                request.credentialIdentifier to assertNotNull(request.crypto2IssuerStoredKey)
            }
            val persistedSidecars = Json.decodeFromString<Map<String, String>>(
                assertNotNull(sidecarPersistence[session.sessionId])
            )

            assertEquals(savedSidecars.getValue("credential"), repairedSidecars.getValue("credential"))
            assertNotEquals(savedSidecars.getValue("credential-2"), repairedSidecars.getValue("credential-2"))
            assertEquals(repairedSidecars, persistedSidecars)
        } finally {
            repository.remove(session.sessionId)
        }
    }

    @Test
    fun concurrentTakeReturnsSessionOnce() = runTest {
        val repository = ConfiguredIssuanceSessionRepository()
        val session = testSession()

        try {
            // Compared against the saved session: save() attaches the crypto2 sidecar, and take()
            // hands that same key material to whichever caller wins the claim.
            val saved = repository.save(session)

            val claims = List(8) {
                async(Dispatchers.Default) { repository.take(session.sessionId) }
            }.awaitAll()

            assertEquals(listOf(saved), claims.filterNotNull())
            assertNull(repository.get(session.sessionId))
        } finally {
            repository.remove(session.sessionId)
        }
    }

    private suspend fun testSession() = IssuanceSession(
        sessionId = "session-${Clock.System.now().toEpochMilliseconds()}",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        issuanceRequests = listOf(
            IssuanceRequest(
                credentialIdentifier = "credential",
                profileId = "profile-id",
                credentialConfigurationId = "identity_credential",
                issuerKey = KeySerialization.serializeKeyToJson(JWKKey.generate(KeyType.secp256r1)).jsonObject,
                credentialData = buildJsonObject {
                    put("given_name", "Jane")
                    put("family_name", "Doe")
                },
                issuerDid = "did:web:issuer.example",
            )
        ),
        expiresAt = Clock.System.now().plus(5.minutes),
    )

    /** Exercises the serialized boundary even when the configured backing store is in memory. */
    private class SerializedSessions(private val raw: Persistence<String>) : Persistence<IssuanceSession>(raw.discriminator, raw.defaultExpiration) {
        override fun get(id: String) = raw[id]?.let(IssuanceSessionStorageCodec::decode)
        override fun getAndRemove(id: String) = raw.getAndRemove(id)?.let(IssuanceSessionStorageCodec::decode)
        override fun set(id: String, value: IssuanceSession) = set(id, value, null)
        override fun set(id: String, value: IssuanceSession, ttl: Duration?) = raw.set(id, Json.encodeToString(value), ttl)
        override fun remove(id: String) = raw.remove(id)
        override fun contains(id: String) = id in raw
        override fun listAllKeys() = raw.listAllKeys()
        override fun getAll() = raw.getAll().map(IssuanceSessionStorageCodec::decode)
        override fun listSize(id: String) = raw.listSize(id)
        override fun listAdd(id: String, value: IssuanceSession, ttl: Duration?) = raw.listAdd(id, Json.encodeToString(value), ttl)
    }

}
