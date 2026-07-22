package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidDocument
import id.walt.webdatafetching.WebDataFetcher
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidWebResolver(private val fetcher: WebDataFetcher) : LocalResolverMethod("web") {

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<DidDocument> {
        val url = resolveDidToUrl(did)

        val response = runCatching {
            fetcher.rawFetch(url).bodyAsText().let {
                DidDocument(jsonObject = Json.parseToJsonElement(it).jsonObject)
            }
        }.onFailure { err ->
            throw IllegalStateException("Could not resolve DID document: $did at $url", err)
        }

        return response
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Deprecated("Use Crypto2DidKeyResolver or Crypto2DidService for key resolution")
    override suspend fun resolveToKey(did: String): Result<Key> {
        // For backward compatibility, return the first key
        return resolveToKeys(did).map { it.firstOrNull() ?: throw NoSuchElementException("No key could be imported") }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Deprecated("Use Crypto2DidKeyResolver or Crypto2DidService for key resolution")
    override suspend fun resolveToKeys(did: String): Result<Set<Key>> {
        val didDocumentResult = resolve(did)
        if (didDocumentResult.isFailure) return Result.failure(didDocumentResult.exceptionOrNull()!!)

        val didDocument = didDocumentResult.getOrNull()
            ?: return Result.failure(IllegalStateException("DID document is null for $did"))

        val verificationMethod = didDocument["verificationMethod"]
            ?: return Result.failure(IllegalStateException("No verification method found in DID document for $did"))

        val verificationArray = verificationMethod.jsonArray

        // Extract both the verification method ID and the public key JWK
        val verificationMethodsWithJwks = verificationArray.mapNotNull { element ->
            runCatching {
                val method = element.jsonObject
                val verificationMethodId = method["id"]?.jsonPrimitive?.contentOrNull
                val publicKeyJwk = method["publicKeyJwk"]?.jsonObject
                    ?: return@runCatching null
                VerificationMethodWithJwk(
                    verificationMethodId = verificationMethodId,
                    publicKeyJwk = json.encodeToString(publicKeyJwk)
                )
            }.getOrNull()
        }

        if (verificationMethodsWithJwks.isEmpty()) {
            return Result.failure(IllegalStateException("No valid public key JWKs found in DID document for $did"))
        }

        return tryConvertVerificationMethodsToKeys(verificationMethodsWithJwks)
    }

    /**
     * Data class to hold verification method ID and its associated JWK
     */
    private data class VerificationMethodWithJwk(
        val verificationMethodId: String?,
        val publicKeyJwk: String
    )

    private fun resolveDidToUrl(did: String): String = DidUtils.identifierFromDid(did)?.let {
        val didParts = it.split(":")

        val domain = didParts[0].replace("%3A", ":")
        val selectedPath = didParts.drop(1)

        val path = when {
            selectedPath.isEmpty() -> "/.well-known/did.json"
            else -> "/${selectedPath.joinToString("/")}/did.json"
        }
        "$urlProtocol://$domain$path"
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

    private suspend fun tryConvertVerificationMethodsToKeys(verificationMethods: List<VerificationMethodWithJwk>): Result<Set<JWKKey>> {
        val keys = mutableSetOf<JWKKey>()

        for (vm in verificationMethods) {
            val result = JWKKey.importJWK(vm.publicKeyJwk)
            if (result.isSuccess) {
                val importedKey = result.getOrThrow()
                val keyWithVmId = if (vm.verificationMethodId != null) {
                    JWKKey(importedKey.exportJWK(), vm.verificationMethodId)
                } else {
                    importedKey
                }
                keys.add(keyWithVmId)
            }
        }

        return if (keys.isNotEmpty()) {
            Result.success(keys)
        } else {
            Result.failure(NoSuchElementException("No keys could be imported from the DID document"))
        }
    }

    companion object {

        private var httpsEnabled: Boolean = true

        val urlProtocol: String
            get() = if (httpsEnabled) "https" else "http"

        fun enableHttps(httpsEnabled: Boolean) {
            this.httpsEnabled = httpsEnabled
        }

        internal val json = Json { ignoreUnknownKeys = true }
    }
}
