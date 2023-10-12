import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TSEKey
import id.walt.crypto.keys.TSEKeyMetadata
import io.ktor.util.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TSEKeyTest {

    private suspend fun test(key: TSEKey) {
        try {
            println("TSEKey: $key")
            val plaintext = "This is a plaintext for ${key.keyType.name}... 123".encodeToByteArray()
            println("Plaintext: ${plaintext.decodeToString()}")

            val signed = key.signRaw(plaintext) as String
            println("Signed: $signed")

            val verified = key.verifyRaw(signed.decodeBase64Bytes(), plaintext)
            println("Verified signature success: ${verified.isSuccess}")
            println("Verified plaintext: ${verified.getOrNull()!!.decodeToString()}")
        } finally {
            println("Deleting $key...")
            key.delete()
        }
    }

    @Test
    fun testAll() = runTest {
        val tseMetadata = TSEKeyMetadata("http://127.0.0.1:8200/v1/transit", "dev-only-token")

        listOf(
            TSEKey.generate(KeyType.Ed25519, tseMetadata),
            TSEKey.generate(KeyType.RSA, tseMetadata),
            TSEKey.generate(KeyType.secp256r1, tseMetadata)
        ).forEach {
            test(it)
        }
    }
}
