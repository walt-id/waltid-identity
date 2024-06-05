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
        "^((https?:)(\\/\\/\\/?)([\\w]*(?::[\\w]*)?@)?([\\d\\w\\.-]+)(?::(\\d+))?)?([\\/\\\\w\\.()-]*)?(?:([?][^#]*)?(#.*)?)*"

    fun new(url: String) = when {
        didPattern.toRegex().matches(url) -> entraStrategy
        urlPattern.toRegex().matches(url) -> defaultStrategy
        else -> error("Not supported status credential url: $url")
    }
}