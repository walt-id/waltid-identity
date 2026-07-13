package id.walt.walletdemo.compose.logic

import io.ktor.http.Url

internal object CredentialTypeIdentifier {
    fun token(rawValue: String): String? {
        val value = rawValue.trim().takeIf { it.isNotBlank() } ?: return null
        return (value.httpUrlToken() ?: value.trailingIdentifierToken())
            .takeIf { it.isNotBlank() }
    }

    private fun String.httpUrlToken(): String? {
        if (!contains("://")) return null
        val parsed = runCatching { Url(this) }.getOrNull()
            ?: return null
        if (parsed.protocol.name !in httpProtocols) return null

        return parsed.encodedPath
            .split('/')
            .lastOrNull { it.isNotBlank() }
    }

    private fun String.trailingIdentifierToken(): String {
        val parts = split(*identifierDelimiters)
            .filter { it.isNotBlank() }
        val last = parts.lastOrNull() ?: return this
        return if (last.all(Char::isDigit) && parts.size >= 2) {
            "${parts[parts.lastIndex - 1]}_$last"
        } else {
            last
        }
    }

    private val httpProtocols = setOf("http", "https")
    private val identifierDelimiters = charArrayOf('/', '#', ':', '.')
}
