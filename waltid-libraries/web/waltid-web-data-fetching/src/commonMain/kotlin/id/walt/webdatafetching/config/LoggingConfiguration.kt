package id.walt.webdatafetching.config

import io.ktor.client.plugins.logging.*
import kotlinx.serialization.Serializable

@Serializable
data class LoggingConfiguration(
    val enable: Boolean = true,
    val format: LoggingFormat = LoggingFormat.Default,
    val level: LogLevel = LogLevel.HEADERS
) {
    companion object {
        val Example = LoggingConfiguration(
            enable = true,
            format = LoggingFormat.Default,
            level = LogLevel.HEADERS
        )
    }
}
