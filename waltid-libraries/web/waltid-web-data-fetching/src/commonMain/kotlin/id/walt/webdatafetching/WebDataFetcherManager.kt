package id.walt.webdatafetching

object WebDataFetcherManager {

    private val fetcherConfigurations = HashMap<String, WebDataFetchingConfiguration>()
    private val defaultConfiguration: WebDataFetchingConfiguration = WebDataFetchingConfiguration.Default

    fun getConfigurationForId(id: String): WebDataFetchingConfiguration {
        return fetcherConfigurations[id] ?: defaultConfiguration
    }

    fun applyConfigurations(configs: Map<String, WebDataFetchingConfiguration>) {
        fetcherConfigurations.putAll(configs)
    }

    fun appendConfiguration(id: String, configuration: WebDataFetchingConfiguration) {
        fetcherConfigurations[id] = configuration
    }
}
