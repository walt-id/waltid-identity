package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.DidDocument
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidEbsiResolver : LocalResolverMethod("ebsi") {

    val httpLogging = false

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<DidDocument> {
        val url = "https://api-conformance.ebsi.eu/did-registry/v5/identifiers/${did}"

        val httpClient = HttpClient() {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            if (httpLogging) {
                install(Logging) {
                    logger = Logger.SIMPLE
                    level = LogLevel.HEADERS
                }
            }
        }

        val response = runCatching {
            DidDocument(
                jsonObject = httpClient.get(url).body<JsonObject>()
            )
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

        val publicKeyJwks = didDocumentResult.getOrNull()!!["verificationMethod"]!!.jsonArray.map {
            runCatching {
                val verificationMethod = it.jsonObject
                val publicKeyJwk = verificationMethod["publicKeyJwk"]!!.jsonObject
                DidWebResolver.json.encodeToString(publicKeyJwk)
            }
        }.filter { it.isSuccess }.map { it.getOrThrow() }

        return tryConvertAnyPublicKeyJwkToKey(publicKeyJwks)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun tryConvertAnyPublicKeyJwkToKey(publicKeyJwks: List<String>): Result<JWKKey> {
        publicKeyJwks.forEach { publicKeyJwk ->
            val result = JWKKey.importJWK(publicKeyJwk)
            if (result.isSuccess && publicKeyJwk.contains("P-256")) return result
        }
        return Result.failure(NoSuchElementException("No key could be imported"))
    }
}