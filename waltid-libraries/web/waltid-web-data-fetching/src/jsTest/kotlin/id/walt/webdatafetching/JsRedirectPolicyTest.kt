package id.walt.webdatafetching

import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.config.url.UrlConfiguration
import id.walt.webdatafetching.ssrf.installPrivateNetworkGuard
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import org.w3c.fetch.Response
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsRedirectPolicyTest {
    @Test
    fun `Fetch exposes redirect hops to the live URL policy`() = runTest {
        val origin = "https://issuer.example"
        val keySet = "https://keys.example/set"
        var policy: UrlConfiguration? = null
        val sends = mutableListOf<Pair<String, String>>()
        HttpClient(Js) {
            followRedirects = true
            engine {
                fetch = { url, options ->
                    sends += url to (options?.redirect as String)
                    Promise.resolve(when (url) {
                        "$origin/metadata" -> Response.redirect(keySet, 302)
                        "$origin/opaque" -> Response.error()
                        else -> Response("ok")
                    })
                }
            }
            installPrivateNetworkGuard(false) { policy }
        }.use { client ->
            client.get("$origin/ping")
            policy = UrlConfiguration(hosts = AllowList(whitelist = listOf("issuer.example")))
            assertFailsWith<IllegalArgumentException> { client.get("$origin/metadata") }
            policy = UrlConfiguration(hosts = AllowList(whitelist = listOf("issuer.example", "keys.example")))
            client.get("$origin/metadata")
            val failure = assertFails { client.get("$origin/opaque") }
            assertTrue(generateSequence(failure) { it.cause }.any { "opaque HTTP response" in it.message.orEmpty() })
            assertEquals(listOf(
                "$origin/ping" to "follow", "$origin/metadata" to "manual",
                "$origin/metadata" to "manual", keySet to "manual", "$origin/opaque" to "manual",
            ), sends)
        }
    }
}
