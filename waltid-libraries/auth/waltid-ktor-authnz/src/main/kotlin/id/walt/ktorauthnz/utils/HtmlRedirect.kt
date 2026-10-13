package id.walt.ktorauthnz.utils

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*
import kotlinx.serialization.json.JsonPrimitive

object HtmlRedirect {

    /**
     * [url] as a JavaScript string literal: JSON-encoded, so quotes and backslashes cannot end it, and with `<`
     * escaped, so a `</script>` in it cannot end the script element.
     */
    internal fun javaScriptString(url: String): String = JsonPrimitive(url).toString().replace("<", "\\u003c")

    suspend fun ApplicationCall.htmlBasedRedirect(redirectUrl: Url) = this.respondHtml {
        head {
            title("Authentication success")
            script(type = ScriptType.textJavaScript) {
                unsafe {
                    raw(
                        // language=javascript
                        "window.location.href = ${javaScriptString(redirectUrl.toString())};"
                    )
                }
            }
            meta {
                httpEquiv = "refresh"
                content = "0;url=$redirectUrl"
            }
            style {
                unsafe {
                    raw(
                        // language=css
                        """
                            @keyframes fadeIn {
                                0%   { visibility: hidden; opacity: 0; }
                                50%  { visibility: hidden; opacity: 0; }
                                100% { visibility: visible; opacity: 1; }
                            }

                            #continue-link {
                                visibility: hidden;
                                opacity: 0;
                                animation: fadeIn 5s linear forwards;
                            }
                            """.trimIndent()
                    )
                }
            }
        }
        body {
            a(href = redirectUrl.toString()) {
                this.id = "continue-link"
                text("The browser redirect appears to not work correctly. Please click this link manually.")
            }
        }
    }

}
