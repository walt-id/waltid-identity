package id.walt.issuer2.repository

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.commons.persistence.Persistence
import id.walt.commons.persistence.RedisPersistence
import id.walt.issuer2.domain.CredentialStatusConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
        expiresAt: Instant,
    ): List<JsonArray>
}

@Serializable
data class CredentialStatusConsumption(
    val reservations: Map<String, CredentialStatusReservation> = emptyMap(),
    val referenceOwners: Map<String, CredentialStatusOwner> = emptyMap(),
)

@Serializable
data class CredentialStatusOwner(val profileId: String, val operationId: String)

@Serializable
data class CredentialStatusReservation(
    /** Operation-scoped digest; raw holder identifiers stay in the expiring session checkpoint. */
    val binding: String,
    val groups: List<JsonArray>,
    val expiresAt: Instant,
    val profileId: String,
) {
    companion object {
        internal fun bindingFor(operationId: String, holderKeys: List<String>): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (operationId + ":" + Json.encodeToString(holderKeys)).encodeToByteArray(),
            ))
    }
}

internal const val GLOBAL_STATUS_CONSUMPTION_ID = ".global-v2"

internal fun configuredCredentialStatusStorage() = ConfiguredPersistence(
    "issuer2_credential_status_consumption",
    encoding = { state: CredentialStatusConsumption -> Json.encodeToString(state) },
    decoding = { Json.decodeFromString<CredentialStatusConsumption>(it) },
)

/** Redis uses incremental global ownership records; injected stores retain an atomic CAS implementation. */
class ConfiguredCredentialStatusConsumptionRepository(
    private val storage: Persistence<CredentialStatusConsumption>,
    durable: Boolean? = null,
    private val now: () -> Instant = { Clock.System.now() },
) : CredentialStatusConsumptionRepository {
    private val backend = (storage as? ConfiguredPersistence<*>)?.underlyingPersistence ?: storage
    private val redis = (backend as? RedisPersistence<*>)?.let {
        RedisCredentialStatusConsumptionRepository(it.pool, it.discriminator, now)
    }
    override val durable = durable ?: (redis != null)

    override suspend fun reserve(
        profileId: String,
        operationId: String,
        holderKeys: List<String>,
        groups: List<CredentialStatusConfiguration.Group>,
        expiresAt: Instant,
    ): List<JsonArray> {
        require(holderKeys.isNotEmpty()) { "At least one verified holder binding is required" }
        redis?.let { return it.reserve(profileId, operationId, holderKeys, groups, expiresAt) }
        val binding = CredentialStatusReservation.bindingFor(operationId, holderKeys)
        while (true) {
            if (expiresAt <= now()) throw CredentialStatusOperationException("Credential status operation expired")
            val expected = storage[GLOBAL_STATUS_CONSUMPTION_ID]
            val current = expected ?: CredentialStatusConsumption()
            current.reservations[operationId]?.let { existing ->
                if (existing.expiresAt <= now()) throw CredentialStatusOperationException("Credential status operation expired")
                if (existing.profileId != profileId || existing.binding != binding || existing.groups.size != holderKeys.size) {
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
                .filter { group -> group.references.none { it in current.referenceOwners || it in selectedReferences } }
                .onEach { selectedReferences += it.references }
                .take(holderKeys.size).toList()
            if (selected.size != holderKeys.size) throw CredentialStatusCapacityException()
            val reservation = CredentialStatusReservation(binding, selected.map { it.statuses }, expiresAt, profileId)
            val replacement = CredentialStatusConsumption(
                reservations = active + (operationId to reservation),
                referenceOwners = current.referenceOwners + selectedReferences.associateWith { CredentialStatusOwner(profileId, operationId) },
            )
            if (storage.compareAndSet(GLOBAL_STATUS_CONSUMPTION_ID, expected, replacement, Duration.INFINITE)) return reservation.groups
        }
    }
}
