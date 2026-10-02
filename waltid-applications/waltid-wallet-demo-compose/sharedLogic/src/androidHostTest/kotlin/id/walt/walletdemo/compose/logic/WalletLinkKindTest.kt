package id.walt.walletdemo.compose.logic

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletLinkKindTest {
    @Test
    fun routesTheSameSyntheticLinksAsTheNativeApp() {
        val directory = requireNotNull(System.getProperty("walletDemoImageFixturesDir"))
        val cases = Json.parseToJsonElement(File(directory, "wallet-link-routing.json").readText()).jsonArray
        cases.forEachIndexed { index, case ->
            val input = case.jsonObject.getValue("input").jsonPrimitive.content
            val expected = WalletLinkKind.valueOf(case.jsonObject.getValue("kind").jsonPrimitive.content)
            assertEquals(expected, WalletLinkKind.classify(input), "Routing fixture $index")
        }
    }
}

class WalletLinkResolverTest {
    @Test fun routesTheSameDocumentsAsTheNativeApp() {
        val directory = requireNotNull(System.getProperty("walletDemoImageFixturesDir"))
        val cases = Json.parseToJsonElement(File(directory, "wallet-link-documents.json").readText()).jsonArray
        val source = "https://example.test/link"
        cases.forEach { fixture ->
            val fields = fixture.jsonObject
            val body = fields.getValue("body").jsonPrimitive.content
            val expected = fields.getValue("kind").jsonPrimitive.content
            if (expected == "Unsupported") kotlin.test.assertFailsWith<WalletLinkException> { routeWalletLinkDocument(source, body) }
            else {
                val resolved = routeWalletLinkDocument(source, body)
                assertEquals(WalletLinkKind.valueOf(expected), resolved.kind)
                val parameter = fields.getValue("parameter").jsonPrimitive.content
                assertEquals(if (parameter == "request_uri") source else body, io.ktor.http.Url(resolved.url).parameters[parameter])
            }
        }
    }

    @Test fun followsRedirectsWithoutGuessingFromPathsOrTryingBothProtocols() = kotlinx.coroutines.test.runTest {
        val fetched = mutableListOf<String>()
        val result = resolveWalletLink("https://example.test/share") { url ->
            fetched += url
            if (fetched.size == 1) WalletLinkDocument(302, location = "/receive")
            else WalletLinkDocument(302, location = "openid4vp://?client_id=demo&dcql_query=%7B%7D")
        }
        assertEquals(listOf("https://example.test/share", "https://example.test/receive"), fetched)
        assertEquals(WalletLinkKind.Presentation, result.kind)
    }

    @Test fun rejectsRedirectLoopsDowngradesOversizeAndErrors() = kotlinx.coroutines.test.runTest {
        for (document in listOf(WalletLinkDocument(302, location = "/link"),
            WalletLinkDocument(302, location = "http://example.test/link"), WalletLinkDocument(503))) {
            kotlin.test.assertFailsWith<WalletLinkException> { resolveWalletLink("https://example.test/link") { document } }
        }
        kotlin.test.assertFailsWith<WalletLinkException> { routeWalletLinkDocument("https://example.test/link", "x".repeat(262_145)) }
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
            resolveWalletLink("https://example.test/link") { throw kotlinx.coroutines.CancellationException() }
        }
    }
}
