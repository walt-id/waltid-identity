package id.walt.commons.web.plugins

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.WebConfig
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

private val webConfig = ConfigManager.getConfig<WebConfig>()

val httpJson = Json {
    explicitNulls = false
    encodeDefaults = true

    prettyPrint = webConfig.humanReadableResultEncoding
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(httpJson)
    }
}
