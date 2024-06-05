import kotlinx.coroutines.test.runTest
import kotlin.test.Test

val didMethodsToTest = listOf("jwk", "web")

class JWKKeyAndDidManagementTest {
    @Test
    fun localDidKeyTest() = runTest {
        testDidMethodsAndKeys(didMethodsToTest)
    }
}
