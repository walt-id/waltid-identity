package id.walt.openid4vci
//
//import id.walt.openid4vci.core.AccessRequestResult
//import id.walt.openid4vci.core.AccessResponseResult
//import id.walt.openid4vci.core.AuthorizeRequestResult
//import id.walt.openid4vci.core.AuthorizeResponseResult
//import id.walt.openid4vci.core.Config
//import id.walt.openid4vci.core.OAuth2Provider
//import id.walt.openid4vci.core.buildProvider
//import kotlinx.coroutines.CoroutineDispatcher
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.async
//import kotlinx.coroutines.awaitAll
//import kotlinx.coroutines.coroutineScope
//import kotlinx.coroutines.test.runTest
//import kotlin.test.Test
//import kotlin.test.assertEquals
//import kotlin.test.assertTrue
//import kotlin.time.ExperimentalTime
//
//class ProviderConcurrencyTest {
//
//    /**
//     * Deterministic stress test without real threads so it runs on
//     * every platform/CI configuration.
//     */
//    @OptIn(ExperimentalCoroutinesApi::class)
//    @Test
//    fun `authorization code flow handles concurrent requests deterministically`() = runTest {
//        val provider = buildProvider(defaultConcurrencyTestConfig())
//        // Uncomment to experiment with unsafe repositories and observe race conditions:
////         val provider = buildProvider(defaultConcurrencyTestConfig().copy(authorizationCodeRepository = UnsafeAuthorizationCodeRepository()))
//        runConcurrentAuthCodeFlows(provider, iterations = 20, parallelism = 500)
//    }
//}
//
