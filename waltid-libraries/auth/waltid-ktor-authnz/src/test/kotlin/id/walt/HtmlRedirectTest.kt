package id.walt

import id.walt.ktorauthnz.utils.HtmlRedirect
import id.walt.ktorauthnz.utils.HtmlRedirect.htmlBasedRedirect
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlRedirectTest {

    private val hostile = "https://app.example/callback?next=\";alert(1);//&x=</script><script>alert(2)</script>"

    @Test
    fun `a redirect URL cannot leave its JavaScript string or the script element`() = testApplication {
        routing { get("/redirect") { call.htmlBasedRedirect(Url(hostile)) } }

        val page = client.get("/redirect").bodyAsText()

        assertFalse(page.contains("<script>alert(2)"), page)
        val script = page.substringAfter("<script").substringAfter(">").substringBefore("</script>")
        assertTrue(script.trim().startsWith("window.location.href = \""), script)
        assertTrue(script.trim().endsWith("\";"), script)
        assertFalse(Regex("""[^\\]";.""").containsMatchIn(script.trim().removeSuffix("\";")), script)
    }

    @Test
    fun `the JavaScript string decodes to the URL`() {
        assertEquals(
            "\"https://a.example/?q=\\\"x\\\\y\\u003c/script>\"",
            HtmlRedirect.javaScriptString("https://a.example/?q=\"x\\y</script>"),
        )
    }
}
