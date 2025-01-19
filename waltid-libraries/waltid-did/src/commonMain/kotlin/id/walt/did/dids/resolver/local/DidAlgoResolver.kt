package id.walt.did.dids.resolver.local

import id.walt.crypto.keys.Key

import id.walt.did.dids.document.DidDocument
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport



expect fun fetchDidDocumentFromAlgorand(didIdentifier: String): DidDocument
@OptIn(ExperimentalJsExport::class)
@JsExport
class DidAlgoResolver : LocalResolverMethod("algo") {
    private val httpClient = HttpClient() //TODO: inject

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        resolveDid(did)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun resolveToKey(did: String): Result<Key> {
        TODO("Not yet implemented")
        // response verificationMethod contains only publicKeyMultibase
        // required: convert it to publicKeyJwk
        // (no functionality provided by crypto, only multibase58btc available)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun resolveDid(did: String): DidDocument {

        // Check if DID is of type `did:algo`
        if (!did.startsWith("did:algo:")) {
            throw IllegalArgumentException("Unsupported DID method: $did")
        }

        // Extract the Algorand address from the DID
        val algorandAddress = parseAlgorandAddressFromDid(did)
        
        // Retrieve DID document from Algorand blockchain
        return fetchDidDocumentFromAlgorand(algorandAddress)
    }

    private fun parseAlgorandAddressFromDid(did: String): String {
        return did.split(":").last() // Extract the last segment as the Algorand address
    }



}
