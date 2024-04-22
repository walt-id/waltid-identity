package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidWebResolver(private val client: HttpClient) : LocalResolverMethod("web") {

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<DidDocument> {
        val url = resolveDidToUrl(did)

        val response = runCatching {
            client.get(url).bodyAsText().let {
                DidDocument(jsonObject = Json.parseToJsonElement(it).jsonObject)
            }
        }

        return response
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun tryConvertAnyPublicKeyJwkToKey(publicKeyJwks: List<String>): Result<JWKKey> {
        publicKeyJwks.forEach { publicKeyJwk ->
            val result = JWKKey.importJWK(publicKeyJwk)
            if (result.isSuccess) return result
        }
        return Result.failure(NoSuchElementException("No key could be imported"))
    }

    companion object {
        const val URL_PROTOCOL = "https"
        val json = Json { ignoreUnknownKeys = true }
    }
}
