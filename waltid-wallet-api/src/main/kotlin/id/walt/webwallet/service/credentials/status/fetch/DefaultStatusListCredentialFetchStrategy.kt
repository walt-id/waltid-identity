package id.walt.webwallet.service.credentials.status.fetch

import kotlinx.serialization.json.JsonObject

class DefaultStatusListCredentialFetchStrategy: StatusListCredentialFetchStrategy {
    override suspend fun fetch(url: String): JsonObject {
        TODO("Not yet implemented")
    }

}