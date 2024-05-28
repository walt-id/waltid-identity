package id.walt.webwallet.e2e.api

import id.walt.crypto.keys.KeyGenerationRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.uuid.UUID
import kotlin.test.assertEquals

class KeyApi(
    private val url: String,
    private val client: HttpClient,
) {
    suspend fun testGenerateKey(walletId: UUID, keyGenerationRequest: KeyGenerationRequest) {
        println("Testing creation of ${keyGenerationRequest.keyType} with ${keyGenerationRequest.backend}...")
        val result = client.post("$url/wallet-api/wallet/$walletId/keys/generate") {
            contentType(ContentType.Application.Json)
            setBody(keyGenerationRequest)
        }
        println("Result for ${keyGenerationRequest.keyType} with ${keyGenerationRequest.backend}: ${result.status}")
        println("Result for ${{}.javaClass.enclosingMethod.name}: ${result.status}")
        assertEquals(HttpStatusCode.OK, result.status)
    }

    suspend fun testImportKey(walletId: UUID, jwkOrPem: String) {
        println("Testing import of jwkOrPem key...")
        val result = client.post("$url/wallet-api/wallet/$walletId/keys/import") {
            contentType(ContentType.Application.Json)
            setBody(jwkOrPem)
        }
        println("Result for ${{}.javaClass.enclosingMethod.name}: ${result.status}")
        assertEquals(HttpStatusCode.OK, result.status)
    }

    suspend fun testLoadKey(walletId: UUID, alias: String) {
        println("Testing loading of key id$alias...")
        val result = client.get("$url/wallet-api/wallet/$walletId/keys/$alias/load")
        println("Result for ${{}.javaClass.enclosingMethod.name}: ${result.status}")
        assertEquals(HttpStatusCode.OK, result.status)
    }

    suspend fun testMetaKey(walletId: UUID, alias: String) {
        println("Testing meta of key id $alias...")
        val result = client.get("$url/wallet-api/wallet/$walletId/keys/$alias/meta")
        println("Result for ${{}.javaClass.enclosingMethod.name}: ${result.status}")
        assertEquals(HttpStatusCode.OK, result.status)
    }

    suspend fun testExportKey(walletId: UUID, alias: String, format: String, includePrivateKey: Boolean) {
        println("Testing exporting of $alias formatted as $format ${includePrivateKey.takeIf { it } ?: "not"} including private key...")
        val result =
            client.get("$url/wallet-api/wallet/$walletId/keys/$alias/export?format=$format&loadPrivateKey=$includePrivateKey")
        println("Result for ${{}.javaClass.enclosingMethod.name}: ${result.status}")
        assertEquals(HttpStatusCode.OK, result.status)
    }

    suspend fun testDeleteKey(walletId: UUID, alias: String) {
        println("Testing meta of key id $alias...")
        val result = client.delete("$url/wallet-api/wallet/$walletId/keys/$alias")
        println("Result for ${{}.javaClass.enclosingMethod.name}: ${result.status}")
        assertEquals(HttpStatusCode.Accepted, result.status)
    }
}