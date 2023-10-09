package id.walt.did.dids.resolver.local

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.LocalKey
import id.walt.did.dids.document.DidDocument
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DidWebResolver : LocalResolverMethod("web") {

    companion object {
        var URL_PROTOCOL = "https"//TODO: fix (exposed for test purpose)
        val json = Json { ignoreUnknownKeys = true }
    }

    private val http = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    fun resolveDidToUrl(did: String): String {
        val didParts = did.removePrefix("did:web:").split(":")

        val domain = didParts[0].replace("%3A", ":")
        val selectedPath = didParts.drop(1)

        val path = when {
            selectedPath.isEmpty() -> "/.well-known/did.json"
            else -> "/${selectedPath.joinToString("/")}/did.json"
        }

        return "$URL_PROTOCOL://$domain$path"
    }

    override suspend fun resolve(did: String): Result<DidDocument> {
        val url = resolveDidToUrl(did)

        val response = runCatching {
            DidDocument(
                jsonObject = http.get(url).body<JsonObject>()
            )
        }

        return response
    }

    suspend fun tryConvertAnyPublicKeyJwkToKey(publicKeyJwks: List<String>): Result<LocalKey> {
        publicKeyJwks.forEach { publicKeyJwk ->
            val result = LocalKey.importJWK(publicKeyJwk)
            if (result.isSuccess) return result
        }
        return Result.failure(NoSuchElementException("No key could be imported"))
    }

    override suspend fun resolveToKey(did: String): Result<Key> {
        val didDocumentResult = resolve(did)
        if (didDocumentResult.isFailure) return Result.failure(didDocumentResult.exceptionOrNull()!!)

        val publicKeyJwks =
            didDocumentResult.getOrNull()!!["verificationMethod"]!!.jsonArray.map {
                runCatching { // TODO: one layer up
                    val verificationMethod = it.jsonObject
                    val publicKeyJwk = verificationMethod["publicKeyJwk"]!!.jsonObject
                    // Todo base58
                    json.encodeToString(publicKeyJwk)
                }
            }.filter { it.isSuccess }.map { it.getOrThrow() }

        return tryConvertAnyPublicKeyJwkToKey(publicKeyJwks)
    }
}
