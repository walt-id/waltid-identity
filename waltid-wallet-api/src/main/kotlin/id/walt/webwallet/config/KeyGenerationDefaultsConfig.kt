package id.walt.webwallet.config

import kotlinx.serialization.json.JsonObject

data class KeyGenerationDefaultsConfig(
    val keyGenerationDefaults: Map<String, JsonObject>
) {
    fun getConfigForBackend(name: String) = keyGenerationDefaults[name]
}
