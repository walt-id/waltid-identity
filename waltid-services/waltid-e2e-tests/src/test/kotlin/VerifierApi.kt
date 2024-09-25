import E2ETestWebService.test
import E2ETestWebService.testWithResult
import id.walt.verifier.oidc.PresentationSessionInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object Verifier {
    class SessionApi(private val client: HttpClient) {
        suspend fun get(verificationId: String) =
            testWithResult("/openid4vc/session/{id} - check if presentation definitions match") {
                client.get("/openid4vc/session/$verificationId").expectSuccess().run {
                    body<PresentationSessionInfo>()
                }
            }
    }

    class VerificationApi(private val client: HttpClient) {
        suspend fun verify(payload: String) =
            client.post("/openid4vc/verify") {
                setBody(Json.decodeFromString<JsonObject>(payload))
            }.expectSuccess().run {
                val url = bodyAsText()
                assert(url.contains("presentation_definition_uri="))
                assert(!url.contains("presentation_definition="))
                bodyAsText()
            }
    }
}
