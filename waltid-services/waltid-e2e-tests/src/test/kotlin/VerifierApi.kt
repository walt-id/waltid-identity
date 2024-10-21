import id.walt.commons.testing.E2ETest.test
import id.walt.verifier.oidc.PresentationSessionInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object Verifier {
    class SessionApi(private val client: HttpClient) {
        suspend fun get(verificationId: String, output: ((PresentationSessionInfo) -> Unit)? = null) =
            test("/openid4vc/session/{id} - check if presentation definitions match") {
                client.get("/openid4vc/session/$verificationId").expectSuccess().apply {
                    output?.invoke(body<PresentationSessionInfo>())
                }
            }
    }

    class VerificationApi(private val client: HttpClient) {
        suspend fun verify(payload: String, output: ((String) -> Unit)? = null) = test("/openid4vc/verify") {
            client.post("/openid4vc/verify") {
                setBody(Json.decodeFromString<JsonObject>(payload))
            }.expectSuccess().apply {
                val url = bodyAsText()
                assert(url.contains("presentation_definition_uri="))
                assert(!url.contains("presentation_definition="))
                output?.invoke(bodyAsText())
            }
        }
    }
}
