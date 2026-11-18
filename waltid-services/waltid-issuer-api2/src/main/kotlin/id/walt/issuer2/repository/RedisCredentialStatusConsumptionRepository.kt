package id.walt.issuer2.repository

import id.walt.issuer2.domain.CredentialStatusConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import redis.clients.jedis.UnifiedJedis
import kotlin.time.Instant

/** All keys share a Redis Cluster slot so a batch can span several status-list URIs atomically. */
internal class CredentialStatusRedisKeys(discriminator: String) {
    private val prefix = discriminator + "_v2:{credential-status}"
    val records = "$prefix:records"
    val deadlines = "$prefix:deadlines"
}

@Serializable
private data class StatusCandidate(val statuses: String, val references: Set<String>)

/** Ownership is global within this store; only bounded candidate and expiry work runs per request. */
internal class RedisCredentialStatusConsumptionRepository(
    private val pool: UnifiedJedis,
    discriminator: String,
    private val now: () -> Instant,
) {
    private val keys = CredentialStatusRedisKeys(discriminator)

    suspend fun reserve(
        profileId: String,
        operationId: String,
        holderKeys: List<String>,
        groups: List<CredentialStatusConfiguration.Group>,
        expiresAt: Instant,
    ): List<JsonArray> = withContext(Dispatchers.IO) {
        // Status JSON stays opaque to Lua: cjson's floating-point numbers cannot preserve every ULong idx.
        val candidates = groups.map { StatusCandidate(it.statuses.toString(), it.references) }
        val result = pool.eval(RESERVE, listOf(keys.records, keys.deadlines), listOf(
            profileId, operationId, CredentialStatusReservation.bindingFor(operationId, holderKeys), holderKeys.size.toString(),
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

    private companion object {
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
            local groups, selected = {}, {}
            for _, candidate in ipairs(cjson.decode(ARGV[7])) do
                local available = true
                for _, ref in ipairs(candidate.references) do
                    if selected[ref] or redis.call('HEXISTS', KEYS[1], 'entry:' .. ref) == 1 then available = false; break end
                end
                if available then
                    table.insert(groups, candidate.statuses)
                    for _, ref in ipairs(candidate.references) do selected[ref] = true end
                    if #groups == count then break end
                end
            end
            if #groups ~= count then return {'capacity'} end
            local reservation = {profileId=profile, binding=binding, groups=groups, expiresAtMillis=expires}
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
