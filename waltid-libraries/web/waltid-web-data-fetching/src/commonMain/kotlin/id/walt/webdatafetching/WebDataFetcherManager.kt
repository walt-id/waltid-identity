package id.walt.webdatafetching

import io.github.oshai.kotlinlogging.KotlinLogging

object WebDataFetcherManager {

    private val log = KotlinLogging.logger { }

    private val fetcherConfigurations = HashMap<String, WebDataFetchingConfiguration>()
    var globalDefaultConfiguration: WebDataFetchingConfiguration = WebDataFetchingConfiguration.Default

    fun getConfigurationForId(id: String, instanceDefaultConfiguration: WebDataFetchingConfiguration?): WebDataFetchingConfiguration {
        return fetcherConfigurations[id]
            ?: instanceDefaultConfiguration
            ?: globalDefaultConfiguration
    }

    fun applyConfigurations(configs: Map<String, WebDataFetchingConfiguration>) {
        log.trace { "Applying configurations for ${configs.keys}" }
        fetcherConfigurations.putAll(configs)
    }

    fun appendConfiguration(id: String, configuration: WebDataFetchingConfiguration) {
        fetcherConfigurations[id] = configuration
    }
}
