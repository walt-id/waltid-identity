package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
        }.onFailure { err ->
            throw IllegalStateException("Could not resolve DID document: $did", err)
        }

        return response
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKey(did: String): Result<Key> {
        // For backward compatibility, return the first key
        return resolveToKeys(did).map { it.firstOrNull() ?: throw NoSuchElementException("No key could be imported") }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKeys(did: String): Result<Set<Key>> {
        val didDocumentResult = resolve(did)
        if (didDocumentResult.isFailure) return Result.failure(didDocumentResult.exceptionOrNull()!!)

        val didDocument = didDocumentResult.getOrNull()
            ?: return Result.failure(IllegalStateException("DID document is null for $did"))

        val verificationMethod = didDocument["verificationMethod"]
            ?: return Result.failure(IllegalStateException("No verification method found in DID document for $did"))

        val verificationArray = verificationMethod.jsonArray

        val publicKeyJwks = verificationArray.mapNotNull { element ->
            runCatching {
                val method = element.jsonObject
                val publicKeyJwk = method["publicKeyJwk"]?.jsonObject
                    ?: return@runCatching null
                json.encodeToString(publicKeyJwk)
            }.getOrNull()
        }

        if (publicKeyJwks.isEmpty()) {
            return Result.failure(IllegalStateException("No valid public key JWKs found in DID document for $did"))
        }

        return tryConvertPublicKeyJwksToKeys(publicKeyJwks)
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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun tryConvertPublicKeyJwksToKeys(publicKeyJwks: List<String>): Result<Set<JWKKey>> {
        val keys = mutableSetOf<JWKKey>()

        for (publicKeyJwk in publicKeyJwks) {
            val result = JWKKey.importJWK(publicKeyJwk)
            if (result.isSuccess) {
                keys.add(result.getOrThrow())
            }
        }

        return if (keys.isNotEmpty()) {
            Result.success(keys)
        } else {
            Result.failure(NoSuchElementException("No keys could be imported from the DID document"))
        }
    }

    companion object {
        const val URL_PROTOCOL = "https"
        internal val json = Json { ignoreUnknownKeys = true }
    }
}
