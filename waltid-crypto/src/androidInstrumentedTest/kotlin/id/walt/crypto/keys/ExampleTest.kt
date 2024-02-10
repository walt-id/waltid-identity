package id.walt.crypto.keys

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.DefaultAsserter.assertTrue

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun textView_rendersHelloAndroid() = runTest{
        assertTrue("Check Android is mentioned", "Hello Android!".contains("Android"))
    }

    @Test
    fun generate_key_pair_using_RSA_algorithm() = runTest {
        val keyPair = AndroidLocalKeyGenerator.generate(KeyType.RSA)
        kotlin.test.assertTrue { keyPair.hasPrivateKey }
        kotlin.test.assertTrue { keyPair.toString().contains("RSA") }
    }
}