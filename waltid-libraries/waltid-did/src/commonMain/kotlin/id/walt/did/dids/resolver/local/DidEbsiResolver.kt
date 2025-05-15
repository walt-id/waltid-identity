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
        val responseConformance = client.get(didConformanceRegistryUrlBaseURL + did){
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
        val didDocumentResult = resolve(did)
        if (didDocumentResult.isFailure) return Result.failure(didDocumentResult.exceptionOrNull()!!)

        val publicKeyJwks = didDocumentResult.getOrNull()!!["verificationMethod"]!!.jsonArray.map {
            runCatching {
                val verificationMethod = it.jsonObject
                val publicKeyJwk = verificationMethod["publicKeyJwk"]!!.jsonObject
                DidWebResolver.json.encodeToString(publicKeyJwk)
            }
        }.filter { it.isSuccess }.map { it.getOrThrow() }

        return tryConvertAnyPublicKeyJwkToKey(publicKeyJwks)
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
