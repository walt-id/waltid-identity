package id.walt.walletdemo.compose.logic

import io.ktor.http.Url

internal object CredentialTypeIdentifier {
    fun token(rawValue: String): String? {
        val value = rawValue.trim().takeIf { it.isNotBlank() } ?: return null
        return (value.httpUrlToken() ?: value.trailingIdentifierToken())
            .takeIf { it.isNotBlank() }
    }

    private fun String.httpUrlToken(): String? {
        val parsed = runCatching { Url(this) }.getOrNull()
            ?: return null
        if (parsed.protocol.name !in httpProtocols) return null

        return parsed.encodedPath
            .split('/')
            .lastOrNull { it.isNotBlank() }
    }

    private fun String.trailingIdentifierToken(): String {
        val index = lastIndexOfAny(identifierDelimiters)
        return if (index >= 0) substring(index + 1) else this
    }

    private val httpProtocols = setOf("http", "https")
    private val identifierDelimiters = charArrayOf('/', '#', ':')
}
