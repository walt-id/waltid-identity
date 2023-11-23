package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidCheqdDocument
import id.walt.did.dids.document.DidDocument
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DidCheqdResolver : LocalResolverMethod("cheqd") {
    private val httpClient = HttpClient() //TODO: inject

    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        resolveDid(did)
    }

    override suspend fun resolveToKey(did: String): Result<Key> {
        TODO("Not yet implemented")
    }

    private val json = Json() { ignoreUnknownKeys = true }

    private fun resolveDid(did: String): DidDocument {
        val response = runBlocking {
            httpClient.get("https://resolver.cheqd.net/1.0/identifiers/${did}") {
                headers {
                    append("contentType", "application/did+ld+json")
                }
            }.bodyAsText()
        }
        val resolution = Json.decodeFromString<JsonObject>(response)

        val didDocument = resolution.jsonObject["didResolutionMetadata"]?.jsonObject?.get("error")?.let {
            throw IllegalArgumentException("Could not resolve did:cheqd, resolver responded: ${it.jsonPrimitive.content}")
        } ?: let {
            resolution.jsonObject["didDocument"]?.jsonObject
                ?: throw IllegalArgumentException("Response for did:cheqd did not contain a DID document!")
        }.let {
            json.decodeFromString<id.walt.did.dids.registrar.local.cheqd.models.job.didstates.finished.DidDocument>(
                it.toString()
            )
        }
        return DidDocument(DidCheqdDocument(didDocument).toMap())
    }
}