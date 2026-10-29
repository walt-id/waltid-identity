package id.walt.webdatafetching.config

import io.ktor.client.plugins.logging.*
import kotlinx.serialization.Serializable

@Serializable
data class LoggingConfiguration(
    val enable: Boolean = true,
    val format: LoggingFormat = LoggingFormat.Default,
    val level: LogLevel = LogLevel.HEADERS,
    /** Header names omitted from logs. Matched without case sensitivity. */
    val sanitizedHeaders: Set<String> = SENSITIVE_HEADERS,
) {
    companion object {
        val SENSITIVE_HEADERS = setOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie")

        val Example = LoggingConfiguration(
            enable = true,
            format = LoggingFormat.Default,
            level = LogLevel.HEADERS
        )
    }
}
