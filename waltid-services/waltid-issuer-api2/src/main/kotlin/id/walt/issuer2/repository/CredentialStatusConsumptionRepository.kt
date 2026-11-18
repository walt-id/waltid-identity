package id.walt.issuer2.repository

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.commons.persistence.Persistence
import id.walt.commons.persistence.RedisPersistence
import id.walt.issuer2.domain.CredentialStatusConfiguration
import id.walt.openid4vci.CredentialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import java.security.MessageDigest
import java.util.HexFormat

class CredentialStatusCapacityException : IllegalStateException("Insufficient unused credential status groups")
class CredentialStatusOperationException(message: String) : IllegalStateException(message)

interface CredentialStatusConsumptionRepository {
    val durable: Boolean

    suspend fun reserve(
        profileId: String,
        operationId: String,
        holderKeys: List<String>,
        groups: List<CredentialStatusConfiguration.Group>,
        count: Int,
        expiresAt: Instant,
    ): List<JsonArray>
}

@Serializable
data class CredentialStatusConsumption(
    val usedReferences: Set<String> = emptySet(),
    val reservations: Map<String, CredentialStatusReservation> = emptyMap(),
    val referenceOwners: Map<String, CredentialStatusOwner> = emptyMap(),
) {
    companion object {
        /** Import old profile-local history conservatively; conflicting pending operations fail closed. */
        internal fun fromLegacyProfiles(profiles: Map<String, CredentialStatusConsumption>, now: Instant): CredentialStatusConsumption {
            val owners = linkedMapOf<String, CredentialStatusOwner>()
            profiles.toSortedMap().forEach { (profileId, state) ->
                state.usedReferences.forEach { owners.putIfAbsent(it, CredentialStatusOwner(profileId)) }
            }
            val operations = linkedMapOf<String, CredentialStatusReservation>()
            profiles.toSortedMap().forEach { (profileId, state) ->
                state.reservations.filterValues { it.expiresAt > now }.forEach { (operationId, reservation) ->
                    val references = reservation.referenceGroups().flatten()
                    references.forEach { owners.putIfAbsent(it, CredentialStatusOwner(profileId)) }
                    val collision = operations[operationId]
                    if (collision != null) {
                        operations[operationId] = collision.copy(conflicted = true)
                    } else {
                        val conflicted = reservation.conflicted || references.any {
                            val owner = owners.getValue(it)
                            owner.profileId != profileId || (owner.operationId != null && owner.operationId != operationId)
                        }
                        operations[operationId] = reservation.copy(profileId = profileId, conflicted = conflicted)
                        if (!conflicted) references.forEach { owners[it] = CredentialStatusOwner(profileId, operationId) }
                    }
                }
            }
            return CredentialStatusConsumption(owners.keys.toSet(), operations, owners)
        }
    }
}

@Serializable
data class CredentialStatusOwner(val profileId: String, val operationId: String? = null)

@Serializable
data class CredentialStatusReservation(
    /** Operation-scoped digest; raw holder identifiers stay in the expiring session checkpoint. */
    val binding: String,
    val groups: List<JsonArray>,
    val expiresAt: Instant,
    val profileId: String? = null,
    val conflicted: Boolean = false,
) {
    /** Old reservations did not persist their format or reference keys. */
    internal fun referenceGroups(): List<Set<String>> = groups.map { group ->
        val format = if (group.first().jsonObject.containsKey("status_list")) CredentialFormat.SD_JWT_VC
            else CredentialFormat.JWT_VC_JSON
        (CredentialStatusConfiguration.parse(JsonArray(listOf(group)), format)
            as CredentialStatusConfiguration.Preallocated).groups.single().references
    }

    companion object {
        internal fun bindingFor(operationId: String, holderKeys: List<String>): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (operationId + ":" + Json.encodeToString(holderKeys)).encodeToByteArray(),
            ))
    }
}

/** Reserved ID cannot collide with a profile ID, which cannot contain a dot. */
internal const val GLOBAL_STATUS_CONSUMPTION_ID = ".global-v2"

/** Redis uses incremental global ownership records; injected stores retain an atomic CAS implementation. */
class ConfiguredCredentialStatusConsumptionRepository(
    private val storage: Persistence<CredentialStatusConsumption> = ConfiguredPersistence(
        "issuer2_credential_status_consumption",
        defaultExpiration = Duration.INFINITE,
        encoding = { Json.encodeToString(CredentialStatusConsumption.serializer(), it) },
        decoding = { Json.decodeFromString(CredentialStatusConsumption.serializer(), it) },
    ),
    override val durable: Boolean = storage is RedisPersistence<*> ||
        (storage is ConfiguredPersistence<*> && storage.underlyingPersistence is RedisPersistence<*>),
    private val now: () -> Instant = { Clock.System.now() },
) : CredentialStatusConsumptionRepository {
    private val redis = when (storage) {
        is RedisPersistence<*> -> storage
        is ConfiguredPersistence<*> -> storage.underlyingPersistence as? RedisPersistence<*>
        else -> null
    }
    private val delegate = redis?.let {
        RedisCredentialStatusConsumptionRepository(it.pool, it.discriminator, ::legacySnapshot, now, durable)
    }

    private fun legacySnapshot(): CredentialStatusConsumption = CredentialStatusConsumption.fromLegacyProfiles(
        storage.listAllKeys().filter { it != GLOBAL_STATUS_CONSUMPTION_ID }
            .mapNotNull { id -> storage[id]?.let { id to it } }.toMap(), now(),
    )

    override suspend fun reserve(
        profileId: String,
        operationId: String,
        holderKeys: List<String>,
        groups: List<CredentialStatusConfiguration.Group>,
        count: Int,
        expiresAt: Instant,
    ): List<JsonArray> {
        require(count > 0)
        delegate?.let { return it.reserve(profileId, operationId, holderKeys, groups, count, expiresAt) }
        val binding = CredentialStatusReservation.bindingFor(operationId, holderKeys)
        while (true) {
            if (expiresAt <= now()) throw CredentialStatusOperationException("Credential status operation expired")
            val expected = storage[GLOBAL_STATUS_CONSUMPTION_ID]
            val current = expected ?: legacySnapshot()
            if (expected == null && current.usedReferences.isNotEmpty()) {
                storage.compareAndSet(GLOBAL_STATUS_CONSUMPTION_ID, null, current, Duration.INFINITE)
                continue
            }
            current.reservations[operationId]?.let { existing ->
                if (existing.expiresAt <= now()) throw CredentialStatusOperationException("Credential status operation expired")
                if (existing.conflicted || existing.profileId != profileId || existing.binding != binding || existing.groups.size != count) {
                    throw CredentialStatusOperationException("Credential request conflicts with its reserved status groups")
                }
                return existing.groups
            }
            val active = current.reservations.filterValues { it.expiresAt > now() }
            if (active.size != current.reservations.size) {
                storage.compareAndSet(GLOBAL_STATUS_CONSUMPTION_ID, expected, current.copy(reservations = active), Duration.INFINITE)
                continue
            }
            val selectedReferences = mutableSetOf<String>()
            val selected = groups.asSequence()
                .filter { group -> group.references.none { it in current.usedReferences || it in selectedReferences } }
                .onEach { selectedReferences += it.references }
                .take(count).toList()
            if (selected.size != count) throw CredentialStatusCapacityException()
            val reservation = CredentialStatusReservation(binding, selected.map { it.statuses }, expiresAt, profileId)
            val replacement = CredentialStatusConsumption(
                usedReferences = current.usedReferences + selectedReferences,
                reservations = active + (operationId to reservation),
                referenceOwners = current.referenceOwners + selectedReferences.associateWith { CredentialStatusOwner(profileId, operationId) },
            )
            if (storage.compareAndSet(GLOBAL_STATUS_CONSUMPTION_ID, expected, replacement, Duration.INFINITE)) return reservation.groups
        }
    }
}
