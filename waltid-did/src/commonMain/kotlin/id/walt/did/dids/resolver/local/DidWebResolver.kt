package id.walt.did.dids.resolver.local

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.LocalKey
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DidWebResolver(private val client: HttpClient) : LocalResolverMethod("web") {

    companion object {
        const val URL_PROTOCOL = "https"
        val json = Json { ignoreUnknownKeys = true }
    }

    private fun resolveDidToUrl(did: String): String = DidUtils.identifierFromDid(did)?.let {
        val didParts = it.split(":")

        val domain = didParts[0].replace("%3A", ":")
        val selectedPath = didParts.drop(1)

        val path = when {
            selectedPath.isEmpty() -> "/.well-known/did.json"
            else -> "/${selectedPath.joinToString("/")}/did.json"
        }

        "$URL_PROTOCOL://$domain$path"
    } ?: throw IllegalArgumentException("Unexpected did format (missing identifier): $did")

    override suspend fun resolve(did: String): Result<DidDocument> {
        val url = resolveDidToUrl(did)

        val response = runCatching {
            DidDocument(
                jsonObject = client.get(url).body<JsonObject>()
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
