package id.walt.webdatafetching

import io.github.oshai.kotlinlogging.KotlinLogging

object WebDataFetcherManager {

    private val log = KotlinLogging.logger { }

    private val fetcherConfigurations = HashMap<String, WebDataFetchingConfiguration>()
    var globalDefaultConfiguration: WebDataFetchingConfiguration = WebDataFetchingConfiguration.Default

    fun getConfigurationForId(id: String, instanceDefaultConfiguration: WebDataFetchingConfiguration?): WebDataFetchingConfiguration {
        // Compose layers: global default < instance default < per-ID override.
        // Each layer only overrides fields that were explicitly set (non-null / non-default).
        var result = globalDefaultConfiguration
        if (instanceDefaultConfiguration != null) result = result.mergeWith(instanceDefaultConfiguration)
        val perIdConfig = fetcherConfigurations[id]
        if (perIdConfig != null) result = result.mergeWith(perIdConfig)
        return result
    }

    fun applyConfigurations(configs: Map<String, WebDataFetchingConfiguration>) {
        log.trace { "Applying configurations for ${configs.keys}" }
        fetcherConfigurations.putAll(configs)
    }

    fun appendConfiguration(id: String, configuration: WebDataFetchingConfiguration) {
        fetcherConfigurations[id] = configuration
    }
}
