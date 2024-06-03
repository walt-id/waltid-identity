package id.walt.webwallet.e2e.cases

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.webwallet.e2e.api.KeyApi
import id.walt.webwallet.utils.JsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import junit.framework.TestCase.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.test.assertTrue

class KeyCases(
    private val walletId: UUID,
    val api: KeyApi
) {
    private val log = KotlinLogging.logger { }

    suspend fun testCreateRSAKey() {
        log.debug { "\nUse Case -> Generate new key of type RSA\n" }
        val result = api.generate(walletId, KeyGenerationRequest(keyType = KeyType.RSA))
        assertEquals(HttpStatusCode.OK, result.status)
    }

    suspend fun deleteKeys() {
        log.debug { "\nUse Case -> Delete Keys\n" }
        api.list(walletId).body<JsonArray>().let { keys ->
            keys.forEach {
                JsonUtils.tryGetData(it.jsonObject, "keyId.id")?.jsonPrimitive?.content?.let {
                    api.delete(walletId, it)
                    log.debug { "Deleted keyId $it" }
                }
            }
        }
    }

    suspend fun testKeys() {
        println("\nUse Case -> List Keys\n")
        val keys = api.list(walletId).let { response ->
            kotlin.test.assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonArray>()
        }
        assertTrue(keys.size > 0)
        val algorithm = keys[0].jsonObject["algorithm"]?.jsonPrimitive?.content
        kotlin.test.assertEquals("RSA", algorithm)
    }
}