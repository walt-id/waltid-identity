package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidEbsiDocument
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.ContentType
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
class DidEbsiResolver(
    private val client: HttpClient,
) : LocalResolverMethod("ebsi") {

    private val didConformanceRegistryUrlBaseURL = "https://api-conformance.ebsi.eu/did-registry/v5/identifiers/"
    private val didPilotRegistryUrlBaseURL = "https://api-pilot.ebsi.eu/did-registry/v5/identifiers/"

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        resolveDid(did)
    }

    private suspend fun resolveDid(did: String): DidDocument {
        val responseConformance = client.get(didConformanceRegistryUrlBaseURL + did) {
            headers {
                append(ContentType, "application/did+json")
                append(HttpHeaders.Accept, "application/did+json")
            }
        }.bodyAsText()
        val docConformance = parseDidDocumentOrNull(responseConformance)
        if (docConformance != null) {
            return docConformance
        }

        val responsePilot = client.get(didPilotRegistryUrlBaseURL + did) {
            headers {
                append(ContentType, "application/did+json")
                append(HttpHeaders.Accept, "application/did+json")
            }
        }.bodyAsText()

        return parseDidDocumentOrNull(responsePilot)
            ?: throw IllegalStateException("Failed to resolve EBSI DID from both environments")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKey(did: String): Result<Key> {
        // For backward compatibility, prioritize secp256r1 (P-256) keys
        val didDocumentResult = resolve(did)
        if (didDocumentResult.isFailure) return Result.failure(didDocumentResult.exceptionOrNull()!!)

        val didDocument = didDocumentResult.getOrNull()
            ?: return Result.failure(IllegalStateException("DID document is null for $did"))

        val verificationMethod = didDocument["verificationMethod"]
            ?: return Result.failure(IllegalStateException("No verification method found in DID document for $did"))

        val verificationArray = verificationMethod.jsonArray

        val publicKeyJwks = verificationArray.mapNotNull { element ->
            runCatching {
                val verificationMethod = element.jsonObject
                val publicKeyJwk = verificationMethod["publicKeyJwk"]?.jsonObject
                    ?: return@runCatching null
                DidWebResolver.json.encodeToString(publicKeyJwk)
            }.getOrNull()
        }

        if (publicKeyJwks.isEmpty()) {
            return Result.failure(IllegalStateException("No valid public key JWKs found in DID document for $did"))
        }

        return tryConvertAnyPublicKeyJwkToKey(publicKeyJwks)
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
                val verificationMethod = element.jsonObject
                val publicKeyJwk = verificationMethod["publicKeyJwk"]?.jsonObject
                    ?: return@runCatching null
                DidWebResolver.json.encodeToString(publicKeyJwk)
            }.getOrNull()
        }

        if (publicKeyJwks.isEmpty()) {
            return Result.failure(IllegalStateException("No valid public key JWKs found in DID document for $did"))
        }

        return tryConvertPublicKeyJwksToKeys(publicKeyJwks)
    }

    /*
    Note from Christos:
    There exist cases of EBSI DID Documents that only have secp256k1. There is nothing invalid
    in not having a secp256r1 key. Hence, this function was changed to prioritize secp256r1 keys,
    but not to return a failure result otherwise.
    * NOTE:
    * */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun tryConvertAnyPublicKeyJwkToKey(publicKeyJwks: List<String>): Result<JWKKey> {
        publicKeyJwks.forEach { publicKeyJwk ->
            val result = JWKKey.importJWK(publicKeyJwk)
            if (result.isSuccess && publicKeyJwk.contains("P-256")) return result
        }
        return JWKKey.importJWK(publicKeyJwks.first())
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

    private fun parseDidDocumentOrNull(json: String): DidDocument? {
        return try {
            DidDocument(
                DidEbsiDocument(
                    DidDocument(
                        jsonObject = Json.parseToJsonElement(json).jsonObject
                    )
                ).toMap()
            )
        } catch (e: Exception) {
            null
        }
    }
}
