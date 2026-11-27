package id.walt.webdatafetching

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object WebDataFetcherManager {

    private val log = KotlinLogging.logger { }

    private data class Layer(
        val configuration: WebDataFetchingConfiguration = WebDataFetchingConfiguration.Default,
        val urlRevision: Long = 0,
    ) {
        fun replace(next: WebDataFetchingConfiguration) =
            Layer(next, if (next.url == configuration.url) urlRevision else urlRevision + 1)
    }

    private data class Settings(val global: Layer = Layer(), val perId: Map<String, Layer> = emptyMap())

    private val settings = MutableStateFlow(Settings())

    var globalDefaultConfiguration: WebDataFetchingConfiguration
        get() = settings.value.global.configuration
        set(value) = settings.update { it.copy(global = it.global.replace(value)) }

    internal fun urlPolicyRevision(id: String): String {
        val current = settings.value
        return "${current.global.urlRevision}:${current.perId[id]?.urlRevision ?: 0}"
    }

    fun getConfigurationForId(id: String, instanceDefaultConfiguration: WebDataFetchingConfiguration?): WebDataFetchingConfiguration {
        // Compose layers: global default < instance default < per-ID override.
        // Each layer only overrides fields that were explicitly set (non-null / non-default).
        val current = settings.value
        var result = current.global.configuration
        if (instanceDefaultConfiguration != null) result = result.mergeWith(instanceDefaultConfiguration)
        val perIdConfig = current.perId[id]?.configuration
        if (perIdConfig != null) result = result.mergeWith(perIdConfig)
        return result
    }

    fun applyConfigurations(configs: Map<String, WebDataFetchingConfiguration>) {
        log.trace { "Applying configurations for ${configs.keys}" }
        settings.update { current ->
            val updated = configs.mapValues { (id, config) -> (current.perId[id] ?: Layer()).replace(config) }
            current.copy(perId = current.perId + updated)
        }
    }

    fun appendConfiguration(id: String, configuration: WebDataFetchingConfiguration) {
        applyConfigurations(mapOf(id to configuration))
    }
}
