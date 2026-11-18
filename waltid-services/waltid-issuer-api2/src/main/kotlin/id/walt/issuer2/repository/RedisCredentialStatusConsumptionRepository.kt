package id.walt.issuer2.repository

import id.walt.issuer2.domain.CredentialStatusConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import redis.clients.jedis.UnifiedJedis
import java.util.UUID
import kotlin.time.Instant

/** All keys share a Redis Cluster slot so a batch can span several status-list URIs atomically. */
internal class CredentialStatusRedisKeys(discriminator: String) {
    private val prefix = discriminator + "_v2:{credential-status}"
    val records = "$prefix:records"
    val deadlines = "$prefix:deadlines"
    val migrationComplete = "$prefix:migrated"
    val migrationLock = "$prefix:migration-lock"
}

@Serializable
private data class StatusCandidate(val statuses: String, val references: Set<String>)

@Serializable
private data class RedisStatusReservation(
    val profileId: String,
    val binding: String,
    val groups: List<String>,
    val references: List<Set<String>>,
    val expiresAtMillis: Long,
    val conflicted: Boolean = false,
)

/** Ownership is global within this store; only bounded candidate and expiry work runs per request. */
internal class RedisCredentialStatusConsumptionRepository(
    private val pool: UnifiedJedis,
    discriminator: String,
    private val legacySnapshot: () -> CredentialStatusConsumption,
    private val now: () -> Instant,
    override val durable: Boolean,
) : CredentialStatusConsumptionRepository {
    private val keys = CredentialStatusRedisKeys(discriminator)
    @Volatile private var migrated = false

    override suspend fun reserve(
        profileId: String,
        operationId: String,
        holderKeys: List<String>,
        groups: List<CredentialStatusConfiguration.Group>,
        count: Int,
        expiresAt: Instant,
    ): List<JsonArray> = withContext(Dispatchers.IO) {
        require(count > 0)
        ensureMigrated(expiresAt)
        // Status JSON stays opaque to Lua: cjson's floating-point numbers cannot preserve every ULong idx.
        val candidates = groups.map { StatusCandidate(it.statuses.toString(), it.references) }
        val result = pool.eval(RESERVE, listOf(keys.records, keys.deadlines), listOf(
            profileId, operationId, CredentialStatusReservation.bindingFor(operationId, holderKeys), count.toString(),
            expiresAt.toEpochMilliseconds().toString(), now().toEpochMilliseconds().toString(),
            Json.encodeToString(candidates), Json.encodeToString(CredentialStatusOwner(profileId, operationId)),
        )) as List<*>
        when (result.first()) {
            "ok" -> result.drop(1).map { Json.parseToJsonElement(it as String).jsonArray }
            "capacity" -> throw CredentialStatusCapacityException()
            "expired" -> throw CredentialStatusOperationException("Credential status operation expired")
            "conflict" -> throw CredentialStatusOperationException("Credential request conflicts with its reserved status groups")
            else -> error("Unexpected credential status reservation result")
        }
    }

    private suspend fun ensureMigrated(expiresAt: Instant) {
        if (migrated) return
        val token = UUID.randomUUID().toString()
        while (true) {
            if (expiresAt <= now()) throw CredentialStatusOperationException("Credential status operation expired")
            when ((pool.eval(ACQUIRE_MIGRATION, listOf(keys.migrationComplete, keys.migrationLock),
                listOf(token, MIGRATION_LEASE_MILLIS)) as Long).toInt()) {
                2 -> { migrated = true; return }
                0 -> delay(25)
                1 -> {
                    try {
                        importLegacyHistory(token)
                        if (pool.eval(FINISH_MIGRATION, listOf(keys.migrationLock, keys.migrationComplete), listOf(token)) == 1L) {
                            migrated = true
                            return
                        }
                    } catch (_: MigrationLeaseLost) {
                        // Another replica owns recovery; its writes are idempotent and fenced by the token.
                    } finally {
                        // A failed connection must not mask the original migration error; the lease also expires.
                        runCatching { pool.eval(RELEASE_MIGRATION, listOf(keys.migrationLock), listOf(token)) }
                    }
                }
            }
        }
    }

    private fun importLegacyHistory(token: String) {
        val snapshot = legacySnapshot()
        for (entries in snapshot.referenceOwners.entries.chunked(128)) {
            val arguments = listOf(token, MIGRATION_LEASE_MILLIS) + entries.flatMap { (reference, owner) ->
                listOf(reference, Json.encodeToString(owner))
            }
            if (pool.eval(IMPORT_ENTRIES, listOf(keys.migrationLock, keys.migrationComplete, keys.records), arguments) != 1L) {
                throw MigrationLeaseLost()
            }
        }
        for (operations in snapshot.reservations.entries.chunked(128)) {
            val arguments = listOf(token, MIGRATION_LEASE_MILLIS) + operations.flatMap { (id, reservation) ->
                listOf(id, Json.encodeToString(RedisStatusReservation(
                    profileId = requireNotNull(reservation.profileId), binding = reservation.binding,
                    groups = reservation.groups.map { it.toString() }, references = reservation.referenceGroups(),
                    expiresAtMillis = reservation.expiresAt.toEpochMilliseconds(), conflicted = reservation.conflicted,
                )))
            }
            if (pool.eval(IMPORT_RESERVATIONS, listOf(keys.migrationLock, keys.migrationComplete,
                    keys.records, keys.deadlines), arguments) != 1L) {
                throw MigrationLeaseLost()
            }
        }
    }

    private class MigrationLeaseLost : IllegalStateException()

    private companion object {
        const val MIGRATION_LEASE_MILLIS = "30000"
        val ACQUIRE_MIGRATION = """
            if redis.call('EXISTS', KEYS[1]) == 1 then return 2 end
            if redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2]) then return 1 end
            return 0
        """.trimIndent()
        val RELEASE_MIGRATION = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
        """.trimIndent()
        val FINISH_MIGRATION = """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[2], '2')
            redis.call('DEL', KEYS[1])
            return 1
        """.trimIndent()
        val IMPORT_ENTRIES = """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            for i = 3, #ARGV, 2 do redis.call('HSETNX', KEYS[3], 'entry:' .. ARGV[i], ARGV[i + 1]) end
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
        """.trimIndent()
        val IMPORT_RESERVATIONS = """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            for i = 3, #ARGV, 2 do
                local operation = ARGV[i]
                local reservation = cjson.decode(ARGV[i + 1])
                for _, refs in ipairs(reservation.references) do
                    for _, ref in ipairs(refs) do
                        local value = redis.call('HGET', KEYS[3], 'entry:' .. ref)
                        local owner = value and cjson.decode(value)
                        if not owner or owner.profileId ~= reservation.profileId or
                            (owner.operationId and owner.operationId ~= operation) then reservation.conflicted = true end
                    end
                end
                if not reservation.conflicted then
                    local owner = cjson.encode({profileId=reservation.profileId, operationId=operation})
                    for _, refs in ipairs(reservation.references) do
                        for _, ref in ipairs(refs) do redis.call('HSET', KEYS[3], 'entry:' .. ref, owner) end
                    end
                end
                local field = 'operation:' .. operation
                local existing = redis.call('HGET', KEYS[3], field)
                if not existing then
                    redis.call('HSET', KEYS[3], field, cjson.encode(reservation))
                    redis.call('ZADD', KEYS[4], reservation.expiresAtMillis, operation)
                else
                    local previous = cjson.decode(existing)
                    if previous.profileId ~= reservation.profileId or previous.binding ~= reservation.binding then
                        previous.conflicted = true
                        redis.call('HSET', KEYS[3], field, cjson.encode(previous))
                    end
                end
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
        """.trimIndent()
        val RESERVE = """
            local profile, operation, binding = ARGV[1], ARGV[2], ARGV[3]
            local count, expires, now = tonumber(ARGV[4]), tonumber(ARGV[5]), tonumber(ARGV[6])
            if expires <= now then return {'expired'} end
            local existing = redis.call('HGET', KEYS[1], 'operation:' .. operation)
            if existing then
                local reservation = cjson.decode(existing)
                if reservation.expiresAtMillis <= now then return {'expired'} end
                if reservation.conflicted or reservation.profileId ~= profile or reservation.binding ~= binding or
                    #reservation.groups ~= count then return {'conflict'} end
                local result = {'ok'}
                for _, group in ipairs(reservation.groups) do table.insert(result, group) end
                return result
            end
            -- Bound expiry maintenance; ownership records never expire with their retry records.
            local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now, 'LIMIT', 0, 128)
            for _, id in ipairs(expired) do
                redis.call('HDEL', KEYS[1], 'operation:' .. id)
                redis.call('ZREM', KEYS[2], id)
            end
            local groups, references, selected = {}, {}, {}
            for _, candidate in ipairs(cjson.decode(ARGV[7])) do
                local available = true
                for _, ref in ipairs(candidate.references) do
                    if selected[ref] or redis.call('HEXISTS', KEYS[1], 'entry:' .. ref) == 1 then available = false; break end
                end
                if available then
                    table.insert(groups, candidate.statuses)
                    table.insert(references, candidate.references)
                    for _, ref in ipairs(candidate.references) do selected[ref] = true end
                    if #groups == count then break end
                end
            end
            if #groups ~= count then return {'capacity'} end
            local reservation = {profileId=profile, binding=binding, groups=groups, references=references, expiresAtMillis=expires}
            local encoded = cjson.encode(reservation)
            local fields = {}
            for ref, _ in pairs(selected) do
                table.insert(fields, 'entry:' .. ref)
                table.insert(fields, ARGV[8])
            end
            table.insert(fields, 'operation:' .. operation)
            table.insert(fields, encoded)
            -- Ownership and its recovery record commit in a single write.
            redis.call('HSET', KEYS[1], unpack(fields))
            redis.call('ZADD', KEYS[2], expires, operation)
            local result = {'ok'}
            for _, group in ipairs(groups) do table.insert(result, group) end
            return result
        """.trimIndent()
    }
}
