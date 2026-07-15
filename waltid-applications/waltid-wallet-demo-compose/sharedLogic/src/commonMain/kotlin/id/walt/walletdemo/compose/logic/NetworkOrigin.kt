package id.walt.walletdemo.compose.logic

import io.ktor.http.Url

internal data class NetworkOrigin(
    val host: String,
) {
    companion object {
        fun parse(rawValue: String): NetworkOrigin? {
            val parsed = runCatching { Url(rawValue.trim()) }.getOrNull()
                ?: return null
            if (parsed.protocol.name !in httpProtocols) return null

            return parsed.host
                .takeIf { it.isNotBlank() }
                ?.let(::NetworkOrigin)
        }

        private val httpProtocols = setOf("http", "https")
    }
}
