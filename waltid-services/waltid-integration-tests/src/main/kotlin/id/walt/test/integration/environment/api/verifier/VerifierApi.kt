package id.walt.test.integration.environment.api.verifier

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import id.walt.verifier.oidc.PresentationSessionInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class VerifierApi(
    private val e2e: E2ETest,
    private val client: HttpClient
) {

    suspend fun getSessionRaw(sessionId: String) =
        client.get("/openid4vc/session/$sessionId")

    suspend fun getSession(sessionId: String): PresentationSessionInfo =
        getSessionRaw(sessionId).let {
            it.expectSuccess()
            it.body<PresentationSessionInfo>()
        }

    suspend fun verifyRaw(payload: String) =
        client.post("/openid4vc/verify") {
            setBody(Json.decodeFromString<JsonObject>(payload))
        }

    suspend fun verify(payload: String) = verifyRaw(payload).let {
        it.expectSuccess()
        val url = it.bodyAsText()
        assert(url.contains("presentation_definition_uri="))
        assert(!url.contains("presentation_definition="))
        url
    }
}