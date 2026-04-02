package id.walt.webdatafetching

object WebDataFetcherManager {

    private val fetcherConfigurations = HashMap<String, WebDataFetchingConfiguration>()

    fun getConfigurationForId(id: String, defaultConfiguration: WebDataFetchingConfiguration?): WebDataFetchingConfiguration {
        return fetcherConfigurations[id]
            ?: defaultConfiguration
            ?: WebDataFetchingConfiguration.Default
    }

    fun applyConfigurations(configs: Map<String, WebDataFetchingConfiguration>) {
        fetcherConfigurations.putAll(configs)
    }

    fun appendConfiguration(id: String, configuration: WebDataFetchingConfiguration) {
        fetcherConfigurations[id] = configuration
    }
}
