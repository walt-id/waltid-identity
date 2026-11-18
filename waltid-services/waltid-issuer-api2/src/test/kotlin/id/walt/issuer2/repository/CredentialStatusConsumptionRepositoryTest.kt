package id.walt.issuer2.repository

import id.walt.commons.persistence.InMemoryPersistence
import id.walt.issuer2.domain.CredentialStatusConfiguration
import id.walt.issuer2.domain.CredentialStatusConfigurationTest.Companion.tokenGroups
import id.walt.openid4vci.CredentialFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class CredentialStatusConsumptionRepositoryTest {
    private val storage = InMemoryPersistence<CredentialStatusConsumption>("status-consumption-test", Duration.INFINITE)
    private var now = Instant.parse("2026-01-01T00:00:00Z")
    private fun repository() = ConfiguredCredentialStatusConsumptionRepository(storage, now = { now })
    private fun groups(vararg indexes: Int) = (CredentialStatusConfiguration.parse(
        tokenGroups(*indexes), CredentialFormat.SD_JWT_VC,
    ) as CredentialStatusConfiguration.Preallocated).groups

    @Test
    fun reservationRetriesRecoverTheSameOrderedGroupsAcrossRepositoryInstances() = runTest {
        val configured = groups(3, 7, 11)
        val selected = repository().reserve("profile", "operation", listOf("key1", "key2"), configured, now + 1.days)
        assertEquals(configured.take(2).map { it.statuses }, selected)
        assertEquals(selected, repository().reserve("profile", "operation", listOf("key1", "key2"), configured.reversed(), now + 1.days))
        assertFailsWith<CredentialStatusOperationException> {
            repository().reserve("profile", "operation", listOf("key2", "key1"), configured, now + 1.days)
        }
        assertFailsWith<CredentialStatusOperationException> {
            repository().reserve("profile", "operation", listOf("different"), configured, now + 1.days)
        }
        assertEquals(configured.last().statuses,
            repository().reserve("profile", "next-operation", listOf("key3"), configured, now + 1.days).single())
    }

    @Test
    fun exhaustedBatchConsumesNothingAndCanBeRetriedWithSmallerCount() = runTest {
        val configured = groups(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            repository().reserve("profile", "operation", emptyList(), configured, now + 1.days)
        }
        assertFailsWith<CredentialStatusCapacityException> {
            repository().reserve("profile", "operation", listOf("key1", "key2", "key3", "key4"), configured, now + 1.days)
        }
        assertNull(storage[GLOBAL_STATUS_CONSUMPTION_ID])
        assertEquals(configured.map { it.statuses },
            repository().reserve("profile", "operation", listOf("key1", "key2", "key3"), configured, now + 1.days))
    }

    @Test
    fun concurrentRequestsAcrossInstancesCannotPartiallyConsumeTheLastBatch() = runTest {
        val configured = groups(1, 2, 3)
        val results = coroutineScope {
            (1..12).map { index -> async(Dispatchers.Default) {
                runCatching { repository().reserve("profile", "operation-" + index, listOf("key1", "key2"), configured, now + 1.days) }
            } }.awaitAll()
        }
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.filter { it.isFailure }.all { it.exceptionOrNull() is CredentialStatusCapacityException })
        assertEquals(2, storage[GLOBAL_STATUS_CONSUMPTION_ID]!!.referenceOwners.size)
    }

    @Test
    fun expiredReservationsAndReorderedExtendedConfigurationNeverResetConsumption() = runTest {
        repository().reserve("profile", "old", listOf("key1", "key2"), groups(1, 2), now + 1.days)
        now += 2.days
        val selected = repository().reserve("profile", "new", listOf("key1", "key2"), groups(2, 3, 1, 4), now + 1.days)
        assertEquals(listOf(3, 4), selected.map { it.single().jsonObject["status_list"]!!.jsonObject["idx"]!!.jsonPrimitive.int })
        assertEquals(4, storage[GLOBAL_STATUS_CONSUMPTION_ID]!!.referenceOwners.size)
        assertEquals(setOf("new"), storage[GLOBAL_STATUS_CONSUMPTION_ID]!!.reservations.keys)
        assertFailsWith<CredentialStatusCapacityException> {
            repository().reserve("profile", "reintroduced", listOf("key"), groups(1, 2), now + 1.days)
        }
    }

    @Test
    fun profilesShareEntryUniquenessAndKeepOwnership() = runTest {
        repository().reserve("profile-A", "first", listOf("A1", "A2"), groups(1, 2, 3), now + 1.days)
        assertFailsWith<CredentialStatusCapacityException> {
            repository().reserve("profile-B", "second", listOf("B1", "B2"), groups(1, 2, 3), now + 1.days)
        }
        val last = repository().reserve("profile-B", "second", listOf("B"), groups(1, 2, 3), now + 1.days)
        assertEquals(groups(3).single().statuses, last.single())
        val owners = storage[GLOBAL_STATUS_CONSUMPTION_ID]!!.referenceOwners
        assertEquals(CredentialStatusOwner("profile-A", "first"), owners.getValue(groups(1).single().references.single()))
        assertEquals(CredentialStatusOwner("profile-B", "second"), owners.getValue(groups(3).single().references.single()))
        assertFailsWith<CredentialStatusOperationException> {
            repository().reserve("profile-B", "first", listOf("A1", "A2"), groups(1, 2, 3), now + 1.days)
        }
    }

    @Test
    fun concurrentProfilesCannotClaimTheSameBatch() = runTest {
        val results = coroutineScope {
            (1..12).map { index -> async(Dispatchers.Default) {
                runCatching { repository().reserve("profile-$index", "request-$index", listOf("key1", "key2"), groups(1, 2, 3), now + 1.days) }
            } }.awaitAll()
        }
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.filter { it.isFailure }.all { it.exceptionOrNull() is CredentialStatusCapacityException })
        assertEquals(2, storage[GLOBAL_STATUS_CONSUMPTION_ID]!!.referenceOwners.size)
    }
}
