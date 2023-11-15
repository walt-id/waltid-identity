package id.walt.issuer.revocation.statuslist2021.storage

import kotlinx.serialization.json.JsonObject

interface StatusListCredentialStorageService {
    fun fetch(url: String): JsonObject?
    fun store(credential: JsonObject, url: String): Unit
}
