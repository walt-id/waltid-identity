package id.walt.cose

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CoseAlgorithmsTest {

    @Test
    fun testAllCoseAlgorithms() = runTest {
        KeyType.entries.forEachNumbered { idx, total, keyType ->
            println("> Testing key type  $idx/$total: $keyType")
            val key = JWKKey.generate(keyType)

            val protectedHeaders = CoseHeaders(algorithm = keyType.toCoseAlgorithm())
            val unprotectedHeaders = CoseHeaders(contentType = CoseContentType.AsInt(11))

            val payload = keyType.oid.encodeToByteArray()
            val externalAad = keyType.oid.reversed().encodeToByteArray()

            val signed = CoseSign1.createAndSign(
                protectedHeaders = protectedHeaders,
                unprotectedHeaders = unprotectedHeaders,
                payload = payload,
                signer = key.toCoseSigner()
            )
            val signedHex = signed.toTagged().toHexString()
            println("Created COSE Sign1: $signed")
            println("= $signedHex")

            val signed2 = CoseSign1.createAndSign(
                protectedHeaders = protectedHeaders,
                unprotectedHeaders = unprotectedHeaders,
                payload = payload,
                signer = key.toCoseSigner(),
                externalAad = externalAad
            )
            val signed2Hex = signed2.toTagged().toHexString()
            println("Created COSE Sign1 with ext. AAD: $signed2")
            println("= $signed2Hex")

            val toVerify1 = CoseSign1.fromTagged(signedHex)
            assertEquals(signed, toVerify1)

            val toVerify2 = CoseSign1.fromTagged(signed2Hex)
            assertEquals(signed2, toVerify2)

            val publicKey = key.getPublicKey().toCoseVerifier()
            val verified1 = toVerify1.verify(publicKey)
            val verified2 = toVerify2.verify(publicKey, externalAad)

            println(
                """
                Verified: $verified1
                Verified w/ external aad: $verified2
            """.trimIndent()
            )

            require(verified1 && verified2) { "Verification failed" }
            println()
        }
    }
}
