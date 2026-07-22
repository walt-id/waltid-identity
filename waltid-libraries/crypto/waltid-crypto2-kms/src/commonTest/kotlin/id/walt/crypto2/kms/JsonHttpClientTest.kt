package id.walt.crypto2.kms

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class JsonHttpClientTest {
    @Test
    fun `request cancellation propagates`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { throw CancellationException("cancelled") }
            }
        }

        assertFailsWith<CancellationException> {
            client.executeJson("test KMS", "https://kms.example", HttpMethod.Post)
        }
    }
}
