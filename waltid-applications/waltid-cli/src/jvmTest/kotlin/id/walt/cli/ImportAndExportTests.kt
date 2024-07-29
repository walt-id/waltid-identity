package id.walt.cli

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportAndExportTests {

    @Test
    @Ignore
    fun testRSA() {
        testBidirectionalConversion(KeyType.RSA) // Not working. Keving is looking at it.
    }

    @Test
    @Ignore
    fun testEd25519() {
        // kotlin.NotImplementedError: Ed25519 keys cannot be exported as PEM yet.
        testBidirectionalConversion(KeyType.Ed25519)
    }

    @Test
    fun testSecp256k1() {
        testBidirectionalConversion(KeyType.secp256k1)
    }

    @Test
    fun testSecp256r1() {
        testBidirectionalConversion(KeyType.secp256r1)
    }

    private fun testBidirectionalConversion(keyType: KeyType) {

        val generatedKey = runBlocking { JWKKey.generate(keyType) }
        val generatedJwk = runBlocking { generatedKey.exportJWK() }

        val importedKeyFromJwk = assertDoesNotThrow {
            runBlocking { JWKKey.importJWK(generatedJwk).getOrThrow() }
        }

        println("Export PEM:")
        val exportedPem = runBlocking { importedKeyFromJwk.exportPEM() }
        println(exportedPem)

        val importedKeyFromPem = assertDoesNotThrow {
            runBlocking { JWKKey.importPEM(exportedPem).getOrThrow() }
        }

        val exportedJwk = runBlocking { importedKeyFromPem.exportJWK() }


        assertEquals(
            expected = Json.parseToJsonElement(generatedJwk).jsonObject.filterKeys { it != "kid" },
            actual = Json.parseToJsonElement(exportedJwk).jsonObject.filterKeys { it != "kid" },
        )
    }
}
