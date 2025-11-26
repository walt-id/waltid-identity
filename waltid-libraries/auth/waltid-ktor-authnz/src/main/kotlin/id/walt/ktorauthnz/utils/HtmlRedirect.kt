package id.walt.ktorauthnz.utils

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

object HtmlRedirect {

    suspend fun ApplicationCall.htmlBasedRedirect(redirectUrl: Url) = this.respondHtml {
        head {
            title("Authentication success")
            script(type = ScriptType.textJavaScript) {
                unsafe { raw(
                    // language=javascript
                    "window.location.href = \"$redirectUrl\";"
                ) }
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
