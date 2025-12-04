package id.walt.openid4vci

import id.walt.openid4vci.core.buildProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ProviderJvmConcurrencyTest {

    @Test
    fun `authorization code flow handles real dispatcher concurrency`() = runBlocking {
        val provider = buildProvider(defaultConcurrencyTestConfig())
        // Uncomment to experiment with unsafe repositories and observe race conditions:
//         val provider = buildProvider(defaultConcurrencyTestConfig().copy(authorizationCodeRepository = UnsafeAuthorizationCodeRepository()))

        runConcurrentAuthCodeFlows(
            provider = provider,
            iterations = 20,
            parallelism = 500,
            dispatcher = Dispatchers.Default,
        )
    }
}
