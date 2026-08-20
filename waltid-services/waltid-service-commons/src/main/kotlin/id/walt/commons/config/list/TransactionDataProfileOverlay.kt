package id.walt.commons.config.list

import id.walt.commons.web.ConflictException
import id.walt.commons.web.WebException
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * Runtime overlay applied on top of HOCON-seeded [TransactionDataProfile] entries.
 *
 * Overlay entries win by `type`. Tombstones hide a seeded type until it is created again.
 * [overrides] and [tombstones] are disjoint: a type is either overridden, hidden, or neither.
 */
@Serializable
data class TransactionDataProfileOverlay(
    val overrides: Map<String, TransactionDataProfile> = emptyMap(),
    val tombstones: Set<String> = emptySet(),
) {
    init {
        require(overrides.keys.none { it in tombstones }) {
            "Transaction data profile overlay overrides and tombstones must be disjoint"
        }
    }

    fun applyTo(seed: List<TransactionDataProfile>): List<TransactionDataProfile> {
        val seen = LinkedHashSet<String>()
        val fromSeed = seed.mapNotNull { profile ->
            val type = profile.type
            if (!seen.add(type) || type in tombstones) null
            else overrides[type] ?: profile
        }
        val extras = overrides.values.filter { it.type !in seen && it.type !in tombstones }
        return fromSeed + extras
    }

    fun toTypeRegistry(seed: List<TransactionDataProfile>) =
        TransactionDataTypeRegistry(applyTo(seed).map { it.type }.toSet())

    fun requireExisting(seed: List<TransactionDataProfile>, type: String): TransactionDataProfile =
        applyTo(seed).find { it.type == type }
            ?: throw WebException(HttpStatusCode.NotFound.value, "Transaction data profile '$type' was not found")

    fun create(
        seed: List<TransactionDataProfile>,
        profile: TransactionDataProfile,
    ): Pair<TransactionDataProfileOverlay, TransactionDataProfile> {
        val normalized = requireValidProfile(profile)
        if (applyTo(seed).any { it.type == normalized.type }) {
            throw ConflictException("Transaction data profile '${normalized.type}' already exists")
        }
        return copy(
            overrides = overrides + (normalized.type to normalized),
            tombstones = tombstones - normalized.type,
        ) to normalized
    }

    fun replace(
        seed: List<TransactionDataProfile>,
        type: String,
        profile: TransactionDataProfile,
    ): Pair<TransactionDataProfileOverlay, TransactionDataProfile> {
        val normalized = requireValidProfile(profile)
        require(type == normalized.type) {
            "Profile type '${normalized.type}' must match path type '$type'"
        }
        requireExisting(seed, type)
        return copy(
            overrides = overrides + (type to normalized),
            tombstones = tombstones - type,
        ) to normalized
    }

    fun delete(seed: List<TransactionDataProfile>, type: String): TransactionDataProfileOverlay {
        requireExisting(seed, type)
        return copy(
            overrides = overrides - type,
            tombstones = tombstones + type,
        )
    }

    companion object {
        fun requireValidProfile(profile: TransactionDataProfile): TransactionDataProfile {
            val type = profile.type.trim()
            require(type.isNotEmpty()) { "Transaction data profile type must not be blank" }
            return profile.copy(type = type)
        }
    }
}
