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
 */
@Serializable
data class TransactionDataProfileOverlay(
    val overrides: Map<String, TransactionDataProfile> = emptyMap(),
    val tombstones: Set<String> = emptySet(),
) {
    fun applyTo(seed: List<TransactionDataProfile>): List<TransactionDataProfile> {
        val seedByType = seed.associateBy { it.type }
        return (seedByType.keys + overrides.keys)
            .filterNot { it in tombstones }
            .map { type -> overrides[type] ?: seedByType.getValue(type) }
            .sortedBy { it.type }
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
