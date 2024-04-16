package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidUtils
import id.walt.did.dids.document.DidCheqdDocument
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidEbsiDocument
import id.walt.did.dids.document.DidEbsiBaseDocument
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidEbsiResolver(private val client: HttpClient) : LocalResolverMethod("ebsi") {
    private val httpClient = HttpClient() //TODO: inject

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<DidDocument> =  runCatching {
        resolveDid(did)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKey(did: String): Result<Key> {
        val didDocumentResult = resolve(did)
        if (didDocumentResult.isFailure) return Result.failure(didDocumentResult.exceptionOrNull()!!)

        val publicKeyJwks = didDocumentResult.getOrNull()!!["verificationMethod"]!!.jsonArray[1].toString()


        return tryConvertAnyPublicKeyToKey(publicKeyJwks)

    }


    private suspend fun resolveDid(did: String): DidDocument {
        val response = httpClient.get("https://api-conformance.ebsi.eu/did-registry/v5/identifiers/${did}")
        val responseText = response.bodyAsText()

        val resolution = runCatching { Json.parseToJsonElement(responseText) }.getOrElse { throw RuntimeException("Illegal non-JSON response (${response.status}), body: >>$responseText<< (end of body), error: >>${it.stackTraceToString()}<<") }

        return DidDocument( DidEbsiBaseDocument().let { Json.encodeToJsonElement(resolution) }.jsonObject.toMap())
    }
}

suspend fun tryConvertAnyPublicKeyToKey(publicKeyJwks: String): Result<JWKKey> {
    val result = JWKKey.importJWK(publicKeyJwks)
    if (result.isSuccess) return result
    return Result.failure(NoSuchElementException("No key could be imported"))
}

