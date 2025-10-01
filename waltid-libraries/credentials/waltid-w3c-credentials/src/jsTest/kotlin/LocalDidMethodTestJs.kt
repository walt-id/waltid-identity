import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyTypes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JWKKeyAndDidManagementTest {

//    val didMethodsToTest = listOf("jwk", "web")
//
//    /** Ed25519 + secp256k1 are not available in Nodejs */
//    private val keyTypes = (KeyTypes.EC_KEYS.filterNot { it == KeyType.secp256k1 } union KeyTypes.RSA_KEYS).toList()

    companion object {
        /* Ed25519 + secp256k1 are not available in Nodejs */
        private val rsaKeys = KeyTypes.RSA_KEYS
        private val ecKeys = (KeyTypes.EC_KEYS.filterNot { it == KeyType.secp256k1 })

        private val didJwk = listOf("jwk")
        private val didWeb = listOf("web")
    }

    /* Generating/Signing with large RSA keys is taking too long on slow CI:
    @Test
    fun testDidJwkRsaKeys() = runTest { testDidMethodsAndKeys(didJwk, rsaKeys) }
     */
    @Test
    fun testDidWebRsaKeys() = runTest { testDidMethodsAndKeys(didWeb, rsaKeys) }

    @Test
    fun testDidJwkEcKeys() = runTest { testDidMethodsAndKeys(didJwk, ecKeys) }
    @Test
    fun testDidWebEcKeys() = runTest { testDidMethodsAndKeys(didWeb, ecKeys) }

    /*@Test
    fun localDidKeyTest() = runTest {
        testDidMethodsAndKeys(didMethodsToTest, keyTypes)
    }*/
}
