package id.walt.commons.config.list

import id.walt.commons.config.ConfigManager
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local transaction-data profile registry: HOCON seed plus a runtime overlay.
 *
 * Overlay state is lost on restart; durable OSS defaults remain in `transaction-data-profiles.conf`.
 */
object TransactionDataProfileService {

    private val overrides = ConcurrentHashMap<String, TransactionDataProfile>()
    private val tombstones = ConcurrentHashMap.newKeySet<String>()

    fun reset() {
        overrides.clear()
        tombstones.clear()
    }

    fun seedProfiles(): List<TransactionDataProfile> =
        runCatching { ConfigManager.getConfig<TransactionDataProfilesConfig>().transactionDataProfiles }
            .getOrDefault(emptyList())

    fun overlay(): TransactionDataProfileOverlay =
        TransactionDataProfileOverlay(
            overrides = overrides.toMap(),
            tombstones = tombstones.toSet(),
        )

    fun list(): List<TransactionDataProfile> = overlay().applyTo(seedProfiles())

    fun get(type: String): TransactionDataProfile = overlay().requireExisting(seedProfiles(), type)

    fun create(profile: TransactionDataProfile): TransactionDataProfile {
        val (next, created) = overlay().create(seedProfiles(), profile)
        replaceOverlay(next)
        return created
    }

    fun replace(type: String, profile: TransactionDataProfile): TransactionDataProfile {
        val (next, updated) = overlay().replace(seedProfiles(), type, profile)
        replaceOverlay(next)
        return updated
    }

    fun delete(type: String) {
        replaceOverlay(overlay().delete(seedProfiles(), type))
    }

    fun toTypeRegistry(): TransactionDataTypeRegistry = overlay().toTypeRegistry(seedProfiles())

    private fun replaceOverlay(next: TransactionDataProfileOverlay) {
        overrides.clear()
        overrides.putAll(next.overrides)
        tombstones.clear()
        tombstones.addAll(next.tombstones)
    }
}
