import kotlinx.coroutines.test.runTest
import kotlin.test.Test

val didMethodsToTest = listOf("key", "web", "cheqd")

class LocalKeyAndDidManagementTest {
    @Test
    fun localDidKeyTest() = runTest {
        testDidMethodsAndKeys(didMethodsToTest)
    }
}