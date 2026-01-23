import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.tse.TSEAuth
import id.walt.crypto.keys.tse.TSEKey
import id.walt.crypto.keys.tse.TSEKeyMetadata
import io.ktor.util.*
import kotlinx.coroutines.test.runTest

class TseAuthTest {

    suspend fun testKeyUsage(auth: TSEAuth) {
        val tseKey = TSEKey.generate(
            KeyType.Ed25519,
            TSEKeyMetadata("http://0.0.0.0:8200/v1/transit", auth)
        )
        val plaintext = "This is a plaintext 123".encodeToByteArray()

        val signed = tseKey.signRaw(plaintext).encodeBase64()

        val verified = tseKey.verifyRaw(signed.decodeBase64Bytes(), plaintext)

        println("TSEKey: ${tseKey.getEncodedPublicKey()}")
        println("Plaintext: ${plaintext.decodeToString()}")
        println("Signed: $signed")
        println("Verified signature success: ${verified.isSuccess}")
        println("Verified plaintext: ${verified.getOrNull()!!.decodeToString()}")

        tseKey.delete()

        check(verified.isSuccess)
    }

//    @Test
    fun testAppRole() = runTest {
        testKeyUsage(TSEAuth(roleId = "6823b3c7-60f7-db0b-c663-7359f17c0c30", secretId = "aba4f28b-524c-db63-bae2-67a0e094f46a"))
    }

//    @Test
    fun testUserPass() = runTest {
        testKeyUsage(TSEAuth(username = "myuser", password = "mypassword"))
    }
}
