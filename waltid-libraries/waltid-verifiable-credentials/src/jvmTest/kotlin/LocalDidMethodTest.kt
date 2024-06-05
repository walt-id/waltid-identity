import kotlinx.coroutines.test.runTest
import kotlin.test.Test

val didMethodsToTest = listOf("key", "jwk", "web") // cheqd removed for now, as it is timing out

class JWKKeyAndDidManagementTest {
    @Test
    fun localDidKeyTest() = runTest {
        testDidMethodsAndKeys(didMethodsToTest)
    }
}
