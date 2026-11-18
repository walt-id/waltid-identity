package id.walt.issuer2.repository

import id.walt.openid4vci.deferred.*
import id.walt.openid4vci.requests.credential.AcceptedCredentialRequest
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.issuer2.service.IssuanceSessionService
import kotlinx.coroutines.*
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.persistence.PersistenceConfiguration
import id.walt.commons.persistence.PersistenceNode
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.domain.IssuanceRequest
import id.walt.issuer2.domain.CredentialStatusConfiguration
import id.walt.issuer2.domain.CredentialStatusConfigurationTest.Companion.tokenGroups
import id.walt.issuer2.domain.CredentialStatusConfigurationTest.Companion.w3cStatus
import id.walt.issuer2.domain.IssuanceResult
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPARRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPreAuthorizedCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredRefreshTokenRepository
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import id.walt.openid4vci.repository.refresh.DefaultRefreshTokenRecord
import id.walt.openid4vci.requests.authorization.DefaultAuthorizationRequest
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import redis.clients.jedis.util.JedisClusterCRC16

@Tag("redis")
@EnabledIfEnvironmentVariable(named = "ISSUER2_REDIS_HOST", matches = ".+")
class ConfiguredRedisRepositoryTest {
    @Test
    fun statusReservationsAreAtomicPermanentAndRecoverAcrossRepositoryInstances() = runTest {
        val host = requireNotNull(System.getenv("ISSUER2_REDIS_HOST"))
        val port = System.getenv("ISSUER2_REDIS_PORT")?.toInt() ?: 6379
        redis.clients.jedis.JedisPooled(host, port).use { redis ->
            val namespace = "status-consumption-" + java.util.UUID.randomUUID()
            fun storage() = id.walt.commons.persistence.RedisPersistence(
                namespace, Duration.INFINITE,
                { value: CredentialStatusConsumption -> Json.encodeToString(value) },
                { value -> Json.decodeFromString<CredentialStatusConsumption>(value) }, redis,
            )
            fun repository() = ConfiguredCredentialStatusConsumptionRepository(storage())
            val keys = CredentialStatusRedisKeys(namespace)
            fun groups(vararg indexes: Int) = (CredentialStatusConfiguration.parse(
                tokenGroups(*indexes), CredentialFormat.SD_JWT_VC,
            ) as CredentialStatusConfiguration.Preallocated).groups
            val deadline = Clock.System.now() + 1.days
            try {
                val reserved = repository().reserve("profile", "first", listOf("A", "B"), groups(1, 2, 3), 2, deadline)
                assertEquals(reserved, repository().reserve("profile", "first", listOf("A", "B"), groups(3, 2, 1), 2, deadline))
                assertEquals(-1L, redis.pttl(keys.records))
                assertEquals(CredentialStatusOwner("profile", "first"), Json.decodeFromString<CredentialStatusOwner>(
                    redis.hget(keys.records, "entry:" + groups(1).single().references.single()),
                ))
                val next = repository().reserve("profile", "after-restart", listOf("C"), groups(2, 1, 4, 3), 1, deadline)
                assertEquals(groups(4).single().statuses, next.single())
                val results = coroutineScope {
                    (1..12).map { index -> async(Dispatchers.IO) {
                        runCatching { repository().reserve("concurrent-" + index, "request-" + index, listOf("key"),
                            groups(10, 11, 12), 2, deadline) }
                    } }.awaitAll()
                }
                assertEquals(1, results.count { it.isSuccess })
                kotlin.test.assertTrue(results.filter { it.isFailure }.all { it.exceptionOrNull() is CredentialStatusCapacityException })
                assertEquals(5, redis.hkeys(keys.records).count { it.startsWith("entry:") })
            } finally {
                redis.del(keys.records, keys.deadlines, keys.migrationComplete, keys.migrationLock)
            }
        }
    }

    @Test
    fun statusProfilesCannotReuseEntriesOrPartiallyConsumeAnExhaustedBatch() = runTest {
        withStatusLedger { redis, storage, keys ->
            fun repository() = ConfiguredCredentialStatusConsumptionRepository(storage)
            val groups = statusGroups(1, 2, 3)
            val deadline = Clock.System.now() + 1.days
            repository().reserve("A", "first", listOf("A"), groups, 2, deadline)
            assertFailsWith<CredentialStatusCapacityException> {
                repository().reserve("B", "second", listOf("B"), groups, 2, deadline)
            }
            assertNull(redis.hget(keys.records, "entry:" + groups.last().references.single()))
            assertEquals(listOf(groups.last().statuses), repository().reserve("B", "second", listOf("B"), groups, 1, deadline))
            assertEquals(CredentialStatusOwner("B", "second"), Json.decodeFromString<CredentialStatusOwner>(
                redis.hget(keys.records, "entry:" + groups.last().references.single()),
            ))
            assertFailsWith<CredentialStatusOperationException> {
                repository().reserve("B", "first", listOf("A"), groups, 2, deadline)
            }
        }
    }

    @Test
    fun statusBatchesPreserveUnsignedIndexesAcrossDifferentLists() = runTest {
        withStatusLedger { redis, storage, keys ->
            val values = JsonArray(listOf("https://status.example.com/A", "https://status.example.com/B").map { uri ->
                buildJsonObject { put("status_list", buildJsonObject {
                    put("uri", uri); put("idx", Json.parseToJsonElement(ULong.MAX_VALUE.toString()))
                }) }
            })
            val groups = (CredentialStatusConfiguration.parse(values, CredentialFormat.SD_JWT_VC)
                as CredentialStatusConfiguration.Preallocated).groups
            val deadline = Clock.System.now() + 1.days
            val first = ConfiguredCredentialStatusConsumptionRepository(storage)
                .reserve("A", "batch", listOf("one", "two"), groups, 2, deadline)
            assertEquals(groups.map { it.statuses }, first)
            assertEquals(first, ConfiguredCredentialStatusConsumptionRepository(storage)
                .reserve("A", "batch", listOf("one", "two"), groups.reversed(), 2, deadline))
            assertFailsWith<CredentialStatusCapacityException> {
                ConfiguredCredentialStatusConsumptionRepository(storage).reserve("B", "next", listOf("key"), groups, 1, deadline)
            }
            assertEquals(2, redis.hkeys(keys.records).count { it.startsWith("entry:") })
            val slots = listOf(keys.records, keys.deadlines, keys.migrationComplete, keys.migrationLock)
                .map { JedisClusterCRC16.getSlot(it) }.toSet()
            assertEquals(1, slots.size, "Batch and migration keys must share a Redis Cluster slot")
        }
    }

    @Test
    fun legacyStatusHistoryMigratesOnceAndPendingBatchesRecoverAfterRestart() = runTest {
        withStatusLedger { redis, storage, keys ->
            var now = Clock.System.now()
            val oldGroups = statusGroups(1, 2)
            val pending = CredentialStatusReservation(CredentialStatusReservation.bindingFor("pending", listOf("A")),
                oldGroups.map { it.statuses }, now + 1.days)
            storage["legacy-A"] = CredentialStatusConsumption(oldGroups.flatMap { it.references }.toSet(), mapOf("pending" to pending))
            storage["legacy-B"] = CredentialStatusConsumption(statusGroups(3).single().references)
            // Simulate an interrupted import whose worker's lease expired before recording completion.
            redis.hset(keys.records, "entry:" + oldGroups.first().references.single(), Json.encodeToString(CredentialStatusOwner("legacy-A")))
            redis.psetex(keys.migrationLock, 1, "dead-worker")
            fun repository() = ConfiguredCredentialStatusConsumptionRepository(storage, now = { now })
            assertEquals(pending.groups, repository().reserve("legacy-A", "pending", listOf("A"), statusGroups(2, 4, 1), 2, now + 1.days))
            assertEquals(CredentialStatusOwner("legacy-A", "pending"), Json.decodeFromString<CredentialStatusOwner>(
                redis.hget(keys.records, "entry:" + oldGroups.first().references.single()),
            ))
            assertFailsWith<CredentialStatusCapacityException> {
                repository().reserve("C", "next", listOf("C"), statusGroups(1, 2, 3, 4), 2, now + 1.days)
            }
            assertEquals(listOf(statusGroups(4).single().statuses),
                repository().reserve("C", "next", listOf("C"), statusGroups(1, 2, 3, 4), 1, now + 1.days))
            assertEquals(pending, storage["legacy-A"]!!.reservations.getValue("pending"))
            assertEquals("2", redis.get(keys.migrationComplete))
            now += 2.days
            repository().reserve("D", "later", listOf("D"), statusGroups(5), 1, now + 1.days)
            assertNull(redis.hget(keys.records, "operation:pending"))
            assertNotNull(redis.hget(keys.records, "entry:" + oldGroups.first().references.single()))
            assertFailsWith<CredentialStatusCapacityException> {
                repository().reserve("E", "spent", listOf("E"), statusGroups(1, 2, 3, 4), 1, now + 1.days)
            }
        }
    }

    @Test
    fun legacyProfilesWithOverlappingPendingStatusesFailClosed() = runTest {
        withStatusLedger { redis, storage, keys ->
            val groups = statusGroups(1)
            val deadline = Clock.System.now() + 1.days
            for (profile in listOf("A", "B")) storage[profile] = CredentialStatusConsumption(groups.single().references,
                mapOf(profile to CredentialStatusReservation(CredentialStatusReservation.bindingFor(profile, listOf("key")),
                    groups.map { it.statuses }, deadline)))
            assertFailsWith<CredentialStatusOperationException> {
                ConfiguredCredentialStatusConsumptionRepository(storage).reserve("B", "B", listOf("key"), statusGroups(1, 2), 1, deadline)
            }
            assertNull(redis.hget(keys.records, "entry:" + statusGroups(2).single().references.single()))
            assertEquals(listOf(statusGroups(2).single().statuses), ConfiguredCredentialStatusConsumptionRepository(storage)
                .reserve("C", "fresh", listOf("key"), statusGroups(1, 2), 1, deadline))
        }
    }

    @Test
    fun w3cStatusPurposesMigrateAndRemainAtomicAcrossProfiles() = runTest {
        withStatusLedger { redis, storage, keys ->
            fun groups(revocation: Int, suspension: Int) = (CredentialStatusConfiguration.parse(
                JsonArray(listOf(JsonArray(listOf(w3cStatus(revocation), w3cStatus(suspension, "suspension"))))),
                CredentialFormat.JWT_VC_JSON,
            ) as CredentialStatusConfiguration.Preallocated).groups
            val first = groups(1, 7).single()
            val second = groups(2, 8).single()
            val deadline = Clock.System.now() + 1.days
            val pending = CredentialStatusReservation(CredentialStatusReservation.bindingFor("pending", listOf("key")),
                listOf(first.statuses), deadline)
            storage["legacy-W3C"] = CredentialStatusConsumption(first.references, mapOf("pending" to pending))
            fun repository() = ConfiguredCredentialStatusConsumptionRepository(storage)
            assertEquals(listOf(first.statuses), repository().reserve("legacy-W3C", "pending", listOf("key"),
                listOf(second, first), 1, deadline))
            assertEquals(listOf(second.statuses), repository().reserve("other-W3C", "next", listOf("key"),
                listOf(first, second), 1, deadline))
            assertEquals(8, redis.hkeys(keys.records).count { it.startsWith("entry:") })
            for (reference in second.references) assertEquals(CredentialStatusOwner("other-W3C", "next"),
                Json.decodeFromString<CredentialStatusOwner>(redis.hget(keys.records, "entry:" + reference)))
            val overlapping = groups(1, 99).single()
            assertFailsWith<CredentialStatusCapacityException> {
                repository().reserve("third-W3C", "third", listOf("key"), listOf(overlapping), 1, deadline)
            }
            for (unused in overlapping.references - first.references) assertNull(redis.hget(keys.records, "entry:" + unused))
        }
    }

    private fun statusGroups(vararg indexes: Int) = (CredentialStatusConfiguration.parse(
        tokenGroups(*indexes), CredentialFormat.SD_JWT_VC,
    ) as CredentialStatusConfiguration.Preallocated).groups

    private suspend fun withStatusLedger(block: suspend (
        redis.clients.jedis.JedisPooled,
        id.walt.commons.persistence.RedisPersistence<CredentialStatusConsumption>,
        CredentialStatusRedisKeys,
    ) -> Unit) {
        val host = requireNotNull(System.getenv("ISSUER2_REDIS_HOST"))
        val port = System.getenv("ISSUER2_REDIS_PORT")?.toInt() ?: 6379
        redis.clients.jedis.JedisPooled(host, port).use { redis ->
            val namespace = "global-status-" + java.util.UUID.randomUUID()
            val keys = CredentialStatusRedisKeys(namespace)
            val storage = id.walt.commons.persistence.RedisPersistence(namespace, Duration.INFINITE,
                { value: CredentialStatusConsumption -> Json.encodeToString(value) },
                { Json.decodeFromString<CredentialStatusConsumption>(it) }, redis)
            try { block(redis, storage, keys) }
            finally {
                storage.listAllKeys().forEach(storage::remove)
                redis.del(keys.records, keys.deadlines, keys.migrationComplete, keys.migrationLock)
            }
        }
    }

    @Test
    fun infiniteRedisExpiryDoesNotResetToFiniteOrOverflow() {
        val host = requireNotNull(System.getenv("ISSUER2_REDIS_HOST"))
        val port = System.getenv("ISSUER2_REDIS_PORT")?.toInt() ?: 6379
        redis.clients.jedis.JedisPooled(host, port).use { redis ->
            val namespace = "permanent-status-" + java.util.UUID.randomUUID()
            val persistence = id.walt.commons.persistence.RedisPersistence<String>(
                namespace, Duration.INFINITE, { it }, { it }, redis,
            )
            try {
                persistence["key"] = "first"
                assertEquals(-1L, redis.pttl(namespace + ":key"))
                kotlin.test.assertTrue(persistence.compareAndSet("key", "first", "second", Duration.INFINITE))
                assertEquals(-1L, redis.pttl(namespace + ":key"))
                persistence.set("key", "third", 1.minutes)
                kotlin.test.assertTrue(persistence.retainForAtLeast("key", Duration.INFINITE))
                assertEquals(-1L, redis.pttl(namespace + ":key"))
                kotlin.test.assertTrue(persistence.retainForAtLeast("key", 5.minutes))
                assertEquals(-1L, redis.pttl(namespace + ":key"))
                persistence.listAdd("list", "first", 1.minutes)
                persistence.listAdd("list", "second", Duration.INFINITE)
                assertEquals(-1L, redis.pttl(namespace + ":list"))
            } finally {
                persistence.remove("key")
                persistence.remove("list")
            }
        }
    }

    @Test
    fun immediateStatusCheckpointAndLeaseSurviveRedisSessionRecovery() = runTest {
        configureRedisPersistence()
        var now = Clock.System.now()
        val repository = ConfiguredIssuanceSessionRepository()
        val original = testSession("status-lease-" + java.util.UUID.randomUUID()).copy(expiresAt = now + 1.days)
        repository.save(original)
        try {
            val first = IssuanceSessionService(repository, now = { now })
            val stale = assertNotNull(first.claimCredentialStatusSession(original.sessionId))
            val identifier = original.issuanceRequests.single().credentialIdentifier
            val operation = first.prepareCredentialStatusOperation(stale, identifier, listOf("key1", "key2"), 2)
            val response = CredentialResponse(listOf(
                IssuedCredential(JsonPrimitive("signed-copy-1")), IssuedCredential(JsonPrimitive("signed-copy-2")),
            ))
            first.checkpointCredentialStatusResponse(stale, identifier, operation.id, response)
            now += 6.minutes
            val restartedRepository = ConfiguredIssuanceSessionRepository()
            val restarted = IssuanceSessionService(restartedRepository, now = { now })
            val recovered = assertNotNull(restarted.claimCredentialStatusSession(original.sessionId))
            val restored = restarted.prepareCredentialStatusOperation(recovered, identifier, listOf("key1", "key2"), 2)
            assertEquals(operation.id, restored.id)
            assertEquals(response, restored.response)
            assertNotNull(restartedRepository.get(original.sessionId)!!.issuanceRequests.single().crypto2IssuerStoredKey)
            assertFailsWith<CredentialStatusOperationException> { first.releaseCredentialStatusSession(stale) }
            val complete = restarted.finishCredentialStatusSession(recovered.copy(
                issuanceResults = mapOf(identifier to IssuanceResult("dc+sd-jwt")),
            ), identifier)
            assertEquals(emptyMap(), complete.credentialStatusIssuance!!.operations)
        } finally { repository.remove(original.sessionId) }
    }

    @Test
    fun redisRetentionDoesNotShortenExistingDeadline() = runTest {
        val host = requireNotNull(System.getenv("ISSUER2_REDIS_HOST"))
        val port = System.getenv("ISSUER2_REDIS_PORT")?.toInt() ?: 6379
        redis.clients.jedis.JedisPooled(host, port).use { redis ->
            val namespace = "deferred-retention-${java.util.UUID.randomUUID()}"
            val persistence = id.walt.commons.persistence.RedisPersistence<String>(namespace, 5.minutes, { it }, { it }, redis)
            try {
                persistence.set("key", "private-key", 1.minutes)
                kotlin.test.assertTrue(persistence.retainForAtLeast("key", 7.days))
                kotlin.test.assertTrue(persistence.retainForAtLeast("key", 1.minutes))
                kotlin.test.assertTrue(redis.pttl("$namespace:key") > 6.days.inWholeMilliseconds)
                assertFalse(persistence.retainForAtLeast("missing", 1.minutes))
            } finally { persistence.remove("key") }
        }
    }

    @Test
    fun configuredRepositoriesCanUseRedisPersistence() = runTest {
        configureRedisPersistence()

        val suffix = Clock.System.now().toEpochMilliseconds().toString()
        val sessionRepository = ConfiguredIssuanceSessionRepository()
        val authorizationCodeRepository = ConfiguredAuthorizationCodeRepository()
        val preAuthorizedCodeRepository = ConfiguredPreAuthorizedCodeRepository()
        val parRepository = ConfiguredPARRepository()
        val refreshTokenRepository = ConfiguredRefreshTokenRepository()

        val session = testSession(suffix)
        val authorizationCode = testAuthorizationCode(suffix).copy(
            pkceBinding = id.walt.openid4vci.pkce.PkceBinding.Challenge(
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "S256"),
        )
        val preAuthorizedCode = testPreAuthorizedCode(suffix)
        val pushedAuthorizationRequest = testPushedAuthorizationRequest(suffix)
        val refreshToken = testRefreshToken(suffix)
        val rotatedRefreshToken = testRefreshToken("$suffix-rotated")

        try {
            // Compared against the saved session because save() attaches the crypto2 sidecar.
            val savedSession = sessionRepository.save(session)
            val restartedSessionRepository = ConfiguredIssuanceSessionRepository()
            assertEquals(savedSession, restartedSessionRepository.get(session.sessionId))
            assertEquals(savedSession, restartedSessionRepository.take(session.sessionId))
            assertNull(sessionRepository.take(session.sessionId))

            authorizationCodeRepository.save(authorizationCode)
            val restartedAuthorizationCodeRepository = ConfiguredAuthorizationCodeRepository()
            assertEquals(authorizationCode, restartedAuthorizationCodeRepository.consume(authorizationCode.code))
            assertNull(authorizationCodeRepository.consume(authorizationCode.code))

            preAuthorizedCodeRepository.save(preAuthorizedCode)
            val restartedPreAuthorizedCodeRepository = ConfiguredPreAuthorizedCodeRepository()
            assertEquals(preAuthorizedCode, restartedPreAuthorizedCodeRepository.get(preAuthorizedCode.code))
            assertEquals(preAuthorizedCode, restartedPreAuthorizedCodeRepository.consume(preAuthorizedCode.code))
            assertNull(preAuthorizedCodeRepository.get(preAuthorizedCode.code))

            parRepository.save(pushedAuthorizationRequest)
            assertEquals(
                pushedAuthorizationRequest,
                parRepository.consume(pushedAuthorizationRequest.requestId, Clock.System.now()),
            )
            assertNull(parRepository.consume(pushedAuthorizationRequest.requestId, Clock.System.now()))

            refreshTokenRepository.save(refreshToken)
            assertEquals(refreshToken, refreshTokenRepository.get(refreshToken.tokenSignature))
            assertEquals(
                refreshToken,
                refreshTokenRepository.rotate(refreshToken.tokenSignature, rotatedRefreshToken),
            )
            assertFalse(refreshTokenRepository.get(refreshToken.tokenSignature)!!.active)
            assertEquals(rotatedRefreshToken, refreshTokenRepository.get(rotatedRefreshToken.tokenSignature))
        } finally {
            sessionRepository.remove(session.sessionId)
            authorizationCodeRepository.consume(authorizationCode.code)
            preAuthorizedCodeRepository.consume(preAuthorizedCode.code)
            parRepository.consume(pushedAuthorizationRequest.requestId, Clock.System.now())
        }
    }

    @Test
    fun deferredTransactionsKeepBindingsAndResponseAcrossRestartAndConcurrentPolling() = runTest {
        configureRedisPersistence()
        val repository = ConfiguredIssuanceSessionRepository()
        val original = testSession("deferred-${java.util.UUID.randomUUID()}").copy(
            deferredStatus = DeferredStatus.PENDING, deferredState = DeferredIssuanceState(),
        )
        repository.save(original)
        try {
            val first = DeferredIssuance(IssuanceSessionService(repository).deferredStore)
            first.start(original.sessionId, Clock.System.now() + 7.days)
            val selected = original.issuanceRequests.single()
            val accepted = AcceptedCredentialRequest(
                DefaultCredentialRequest(
                    client = DefaultClient("client", emptyList(), emptySet(), emptySet()),
                    credentialIdentifier = selected.credentialIdentifier, credentialConfigurationId = null,
                    proofs = null, credentialResponseEncryption = null, accessTokenClientId = "client",
                ), selected.credentialIdentifier, selected.credentialConfigurationId,
                CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "https://example.com/type"), emptyList(),
            )
            val transactions = coroutineScope {
                (1..12).map { async(Dispatchers.IO) { first.accept(original.sessionId, accepted) } }.awaitAll()
            }
            val transaction = transactions.first()
            assertEquals(1, transactions.map { it.id }.distinct().size)
            first.decide(original.sessionId, DeferredStatus.READY)
            val restartedRepository = ConfiguredIssuanceSessionRepository()
            val restarted = DeferredIssuance(IssuanceSessionService(restartedRepository).deferredStore)
            assertEquals(transaction, restarted.accept(original.sessionId, accepted))
            val claims = coroutineScope { (1..12).map { index -> async(Dispatchers.IO) {
                (if (index % 2 == 0) first else restarted).collect(original.sessionId, transaction.id, setOf(selected.credentialIdentifier), "client")
            } }.awaitAll() }
            val claim = claims.filterIsInstance<DeferredIssuance.Collection.Claimed>().single().transaction
            val response = CredentialResponse(listOf(IssuedCredential(JsonPrimitive("signed-once"))))
            first.checkpoint(original.sessionId, claim, response)
            first.release(original.sessionId, claim)
            val recovered = assertIs<DeferredIssuance.Collection.Claimed>(restarted.collect(original.sessionId, transaction.id, setOf(selected.credentialIdentifier), "client")).transaction
            assertEquals(response, recovered.response)
            assertEquals(accepted, recovered.accepted)
            assertNotNull(restartedRepository.get(original.sessionId)!!.issuanceRequests.single().crypto2IssuerStoredKey)
            restarted.consume(original.sessionId, recovered)
            assertEquals("invalid_transaction_id", assertFailsWith<DeferredProtocolException> {
                first.collect(original.sessionId, transaction.id, setOf(selected.credentialIdentifier), "client")
            }.error.error)
        } finally { repository.remove(original.sessionId) }
    }

    private fun configureRedisPersistence() {
        ConfigManager.preclear()
        FeatureManager.preclear()

        val host = requireNotNull(System.getenv("ISSUER2_REDIS_HOST"))
        val port = System.getenv("ISSUER2_REDIS_PORT")?.toIntOrNull() ?: 6379
        val user = System.getenv("ISSUER2_REDIS_USER")?.takeIf { it.isNotBlank() }
        val password = System.getenv("ISSUER2_REDIS_PASSWORD")?.takeIf { it.isNotBlank() }

        ConfigManager.preloadAndRegisterConfig(
            "persistence",
            PersistenceConfiguration(
                type = "redis",
                nodes = listOf(PersistenceNode(host = host, port = port)),
                user = user,
                password = password,
            )
        )
        ConfigManager.loadConfigs()
        FeatureManager.enabledFeatures.add(CommonsFeatureCatalog.persistenceFeature.name)
    }

    private suspend fun testSession(suffix: String) = IssuanceSession(
        sessionId = "redis-session-$suffix",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        issuanceRequests = listOf(
            IssuanceRequest(
                credentialIdentifier = "credential-$suffix",
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

    private fun testAuthorizationCode(suffix: String) = DefaultAuthorizationCodeRecord(
        pkceBinding = id.walt.openid4vci.pkce.PkceBinding.NotUsed,
        code = "redis-auth-code-$suffix",
        clientId = "wallet-client",
        redirectUri = "https://wallet.example/callback",
        grantedScopes = setOf("openid"),
        grantedAudience = setOf("issuer2"),
        session = DefaultSession(subject = "subject"),
        expiresAt = Clock.System.now().plus(5.minutes),
    )

    private fun testPreAuthorizedCode(suffix: String) = DefaultPreAuthorizedCodeRecord(
        code = "redis-pre-auth-code-$suffix",
        clientId = null,
        txCode = TxCode(inputMode = "numeric", length = 6, description = "PIN"),
        txCodeValue = "hashed-pin",
        grantedScopes = setOf("openid"),
        grantedAudience = setOf("issuer2"),
        session = DefaultSession(subject = "subject"),
        expiresAt = Clock.System.now().plus(5.minutes),
        issuanceSessionId = "redis-session-$suffix",
    )

    private fun testPushedAuthorizationRequest(suffix: String): DefaultPARRecord {
        val now = Clock.System.now()
        return DefaultPARRecord(
            requestId = "redis-par-$suffix",
            authorizationRequest = DefaultAuthorizationRequest(
                client = DefaultClient("wallet-client", listOf("https://wallet.example/callback"), setOf("authorization_code"), setOf("code")),
                responseTypes = setOf("code"), redirectUri = "https://wallet.example/callback", state = null,
                requestedScopes = setOf("openid"),
                requestForm = mapOf(
                    "client_id" to listOf("wallet-client"),
                    "response_type" to listOf("code"),
                    "redirect_uri" to listOf("https://wallet.example/callback"),
                    "scope" to listOf("openid"),
                ),
            ),
            createdAt = now,
            expiresAt = now.plus(5.minutes),
        )
    }

    private fun testRefreshToken(suffix: String): DefaultRefreshTokenRecord {
        val refreshTokenExpiresAt = timestampOffset(5.minutes)
        val session = DefaultSession(
            subject = "subject",
            expiresAt = mapOf(
                TokenType.ACCESS_TOKEN to timestampOffset(5.minutes),
                TokenType.REFRESH_TOKEN to refreshTokenExpiresAt,
            ),
            customAttributes = mapOf("issuance_session_id" to "redis-session-$suffix"),
        )
        val client = DefaultClient(
            id = "wallet-client",
            redirectUris = listOf("https://wallet.example/callback"),
            grantTypes = setOf("authorization_code", "refresh_token"),
            responseTypes = setOf("code"),
            scopes = setOf("openid"),
            audience = setOf("issuer2"),
        )
        val accessTokenRequest = DefaultAccessTokenRequest(
            id = "redis-access-token-request-$suffix",
            requestedAt = timestampOffset(0.minutes),
            client = client,
            grantTypes = setOf("authorization_code"),
            handledGrantTypes = setOf("authorization_code"),
            requestedScopes = setOf("openid"),
            grantedScopes = setOf("openid"),
            requestedAudience = setOf("issuer2"),
            grantedAudience = setOf("issuer2"),
            requestForm = mapOf(
                "grant_type" to listOf("authorization_code"),
                "client_id" to listOf(client.id),
            ),
            session = session,
            issClaim = "http://localhost",
        )

        return DefaultRefreshTokenRecord(
            dpopJwkThumbprint = "verified-wallet-thumbprint",
            tokenSignature = "redis-refresh-token-$suffix",
            active = true,
            accessTokenRequest = accessTokenRequest,
            accessTokenSignature = "redis-access-token-$suffix",
            clientId = client.id,
            grantedScopes = setOf("openid"),
            grantedAudience = setOf("issuer2"),
            session = session,
            expiresAt = refreshTokenExpiresAt,
        )
    }

    private fun timestampOffset(duration: kotlin.time.Duration): Instant =
        Instant.fromEpochMilliseconds(Clock.System.now().plus(duration).toEpochMilliseconds())
}
