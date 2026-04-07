package id.walt.trust.service.config

import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

private val log = KotlinLogging.logger {}

/**
 * Configuration for the trust-registry service.
 * Source bootstrap URLs can be configured here; they are loaded on startup.
 */
@Serializable
data class TrustRegistryServiceConfig(
    val bootstrapSources: List<BootstrapSource> = emptyList(),
    val enableSyntheticSources: Boolean = true,
    val failOnStaleSource: Boolean = false,
    val allowDemoSkipSignatureValidation: Boolean = true
)

@Serializable
data class BootstrapSource(
    val sourceId: String,
    val url: String,
    val sourceFamily: String = "LOTE"
)

/**
 * Singleton holding the initialized trust-registry service instance.
 */
object TrustRegistryConfig {

    lateinit var service: DefaultTrustRegistryService
        private set

    fun init() {
        log.info { "Initializing Trust Registry service..." }
        val store = InMemoryTrustStore()
        service = DefaultTrustRegistryService(store)
        log.info { "Trust Registry service initialized (in-memory store)" }
    }
}
