package id.walt.commons.config.list

import id.walt.commons.config.ConfigManager
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry

/**
 * Process-local transaction-data profile registry: HOCON seed plus a runtime overlay.
 *
 * Overlay state is lost on restart; durable OSS defaults remain in `transaction-data-profiles.conf`.
 */
object TransactionDataProfileService {

    private val lock = Any()

    @Volatile
    private var overlayState = TransactionDataProfileOverlay()

    fun reset() {
        synchronized(lock) {
            overlayState = TransactionDataProfileOverlay()
        }
    }

    fun seedProfiles(): List<TransactionDataProfile> =
        runCatching { ConfigManager.getConfig<TransactionDataProfilesConfig>().transactionDataProfiles }
            .getOrDefault(emptyList())

    fun overlay(): TransactionDataProfileOverlay = overlayState

    fun list(): List<TransactionDataProfile> = overlay().applyTo(seedProfiles())

    fun get(type: String): TransactionDataProfile = overlay().requireExisting(seedProfiles(), type)

    fun create(profile: TransactionDataProfile): TransactionDataProfile =
        mutate { overlay, seed -> overlay.create(seed, profile) }

    fun replace(type: String, profile: TransactionDataProfile): TransactionDataProfile =
        mutate { overlay, seed -> overlay.replace(seed, type, profile) }

    fun delete(type: String) {
        mutate { overlay, seed -> overlay.delete(seed, type) to Unit }
    }

    fun toTypeRegistry(): TransactionDataTypeRegistry = overlay().toTypeRegistry(seedProfiles())

    private fun <T> mutate(
        compute: (TransactionDataProfileOverlay, List<TransactionDataProfile>) -> Pair<TransactionDataProfileOverlay, T>,
    ): T =
        synchronized(lock) {
            val (next, result) = compute(overlayState, seedProfiles())
            overlayState = next
            result
        }
}
