package id.walt.webdatafetching

import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.config.TimeoutConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class BasicTest {

    companion object {
        val EXAMPLE_URL =
            Url("https://raw.githubusercontent.com/walt-id/waltid-identity/d550f21916b3c3551f23711ecf2c567d01d3cd48/waltid-services/waltid-integration-tests/src/main/resources/issuance/key.json")
    }

    @Test
    fun basicTest() = runTest {
        val dataFetcher = WebDataFetcher<JsonObject>("test-basic")

        val result = dataFetcher.fetch<JsonObject>(EXAMPLE_URL)
        println(result)
        assertContains(result, "jwk")
    }

    @Test
    fun urlFilterTest() = runTest {
        WebDataFetcherManager.appendConfiguration(
            "test-url-filter",
            WebDataFetchingConfiguration(url = UrlConfiguration(urls = AllowList(blacklist = listOf(EXAMPLE_URL))))
        )

        val dataFetcher = WebDataFetcher<JsonObject>("test-url-filter")
        assertFailsWith<IllegalArgumentException> {
            dataFetcher.fetch<JsonObject>(EXAMPLE_URL)
        }
    }

    @Test
    fun timeoutTest() = runTest {
        WebDataFetcherManager.appendConfiguration(
            "test-timeout",
            WebDataFetchingConfiguration(timeouts = TimeoutConfiguration(1.milliseconds, 1.milliseconds, 1.milliseconds))
        )

        val dataFetcher = WebDataFetcher<JsonObject>("test-timeout")
        val url =
            "https://raw.githubusercontent.com/walt-id/waltid-identity/d550f21916b3c3551f23711ecf2c567d01d3cd48/waltid-services/waltid-integration-tests/src/main/resources/issuance/key.json"
        assertFailsWith<DataFetchingException> {
            dataFetcher.fetch<JsonObject>(url)
        }
    }

}
