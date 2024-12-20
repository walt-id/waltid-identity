package id.walt.webwallet.service.credentials.status.fetch

import kotlinx.serialization.json.JsonObject

interface StatusListCredentialFetchStrategy {
    suspend fun fetch(url: String): JsonObject
}

class StatusListCredentialFetchFactory(
    private val defaultStrategy: DefaultStatusListCredentialFetchStrategy,
    private val entraStrategy: EntraStatusListCredentialFetchStrategy,
) {
    private val didPattern = "^did:([^:]+):(.+)"
    private val urlPattern =
        "^https?:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,}\\.[a-zA-Z0-9()]{1,}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&\\/=]*)\$"

    fun new(url: String) = when {
        didPattern.toRegex().matches(url) -> entraStrategy
        urlPattern.toRegex().matches(url) -> defaultStrategy
        else -> error("Not supported status credential url: $url")
    }
}