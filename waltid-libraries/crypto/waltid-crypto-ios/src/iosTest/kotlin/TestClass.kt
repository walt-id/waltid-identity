import id.walt.crypto.IosKeys
import id.walt.crypto.P256Key
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class TestClass {
    @Test
    fun testFun() {
        runBlocking {
            P256Key.create("kid","appId").let {
                print(it.exportJWK())
            }
        }

    }
}