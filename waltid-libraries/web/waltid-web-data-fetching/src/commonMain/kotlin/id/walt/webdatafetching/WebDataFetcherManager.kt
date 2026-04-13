package id.walt.webdatafetching

import io.github.oshai.kotlinlogging.KotlinLogging

object WebDataFetcherManager {

    private val log = KotlinLogging.logger { }

    private val fetcherConfigurations = HashMap<String, WebDataFetchingConfiguration>()
    var defaultConfiguration: WebDataFetchingConfiguration = WebDataFetchingConfiguration.Default

    fun getConfigurationForId(id: String, defaultConfiguration: WebDataFetchingConfiguration?): WebDataFetchingConfiguration {
        return fetcherConfigurations[id]
            ?: defaultConfiguration
            ?: WebDataFetchingConfiguration.Default
    }

    fun applyConfigurations(configs: Map<String, WebDataFetchingConfiguration>) {
        log.trace { "Applying configurations for ${configs.keys}" }
        fetcherConfigurations.putAll(configs)
    }

    fun appendConfiguration(id: String, configuration: WebDataFetchingConfiguration) {
        fetcherConfigurations[id] = configuration
    }
}
