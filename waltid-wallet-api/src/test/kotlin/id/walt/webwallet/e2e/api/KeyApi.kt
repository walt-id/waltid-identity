package id.walt.webwallet.e2e.api

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.did.utils.ExtensionMethods.ensurePrefix
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.uuid.UUID

class KeyApi(
    private val url: String,
    private val client: HttpClient,
) {
    private val apiUrl = "$url/wallet-api/wallet/%s/keys"

    suspend fun generate(walletId: UUID, keyGenerationRequest: KeyGenerationRequest) = let {
        println("Generating key ${keyGenerationRequest.keyType} with ${keyGenerationRequest.backend}...")
        println("Endpoint = ${resolveEndpoint(walletId, "/generate")}")
        client.post(resolveEndpoint(walletId, "/generate")) {
            contentType(ContentType.Application.Json)
            setBody(keyGenerationRequest)
        }
    }

    suspend fun import(walletId: UUID, jwkOrPem: String) = let {
        println("Import of jwkOrPem key...")
        client.post(resolveEndpoint(walletId, "/import")) {
            contentType(ContentType.Application.Json)
            setBody(jwkOrPem)
        }
    }

    suspend fun load(walletId: UUID, alias: String) = let {
        println("Loading key id$alias...")
        client.get("$url/wallet-api/wallet/$walletId/keys/$alias/load")
    }

    suspend fun getMeta(walletId: UUID, alias: String) = let {
        println("Get meta of key id $alias...")
        client.get(resolveEndpoint(walletId, "/$alias/meta"))
    }

    suspend fun export(walletId: UUID, alias: String, format: String, includePrivateKey: Boolean) = let {
        println("Export key $alias formatted as $format ${includePrivateKey.takeIf { it } ?: "not"} including private key...")
        client.get(resolveEndpoint(walletId, "/$alias/export?format=$format&loadPrivateKey=$includePrivateKey"))
    }

    suspend fun delete(walletId: UUID, alias: String) = let {
        println("Delete key id $alias...")
        client.delete(resolveEndpoint(walletId, "/$alias"))
    }

    suspend fun list(walletId: UUID) = let {
        println("List Keys...")
        client.get(resolveEndpoint(walletId))
    }

    private fun resolveEndpoint(walletId: UUID, path: String? = null) =
        String.format(apiUrl, walletId.toString()).removeSuffix("/").plus(path?.ensurePrefix("/") ?: "")
}