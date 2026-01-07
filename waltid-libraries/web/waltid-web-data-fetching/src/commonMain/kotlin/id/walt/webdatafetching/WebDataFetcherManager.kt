package id.walt.webdatafetching

object WebDataFetcherManager {

    val fetcherConfigurations = HashMap<String, WebDataFetchingConfiguration>()
    val defaultConfiguration: WebDataFetchingConfiguration = WebDataFetchingConfiguration.Default

    fun getConfigurationForId(id: String): WebDataFetchingConfiguration {
        return fetcherConfigurations[id] ?: defaultConfiguration
    }

    fun applyConfigurations(configs: Map<String, WebDataFetchingConfiguration>) {
        fetcherConfigurations.putAll(configs)
    }
}
