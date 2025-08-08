import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyTypes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

val didMethodsToTest = listOf("jwk", "web")

class JWKKeyAndDidManagementTest {

    /** Ed25519 + secp256k1 are not available in Nodejs */
    private val keyTypes = (KeyTypes.EC_KEYS.filterNot { it == KeyType.secp256k1 } union KeyTypes.RSA_KEYS).toList()

    @Test
    fun localDidKeyTest() = runTest {
        testDidMethodsAndKeys(didMethodsToTest, keyTypes)
    }
}
