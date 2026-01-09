package id.walt.webdatafetching

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContains

class BasicTest {

    @Test
    fun basicWebDataFetcherTest() = runTest {
        val dataFetcher = WebDataFetcher<JsonObject>("test1")

        val url = "https://raw.githubusercontent.com/walt-id/waltid-identity/d550f21916b3c3551f23711ecf2c567d01d3cd48/waltid-services/waltid-integration-tests/src/main/resources/issuance/key.json"
        val result = dataFetcher.fetch<JsonObject>(url)
        assertContains(result, "jwk")
    }

}
