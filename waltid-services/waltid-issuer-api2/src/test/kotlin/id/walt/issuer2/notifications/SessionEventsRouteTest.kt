package id.walt.issuer2.notifications

import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readUTF8Line
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The session event stream answers an unknown session with the 404 the client can read. Ktor's `sse { }` block
 * runs after the 200 event-stream response has started, so a lookup inside it could only end the stream empty.
 */
class SessionEventsRouteTest {

    @AfterEach
    fun cleanup() = clearIssuer2TestEnvironment()

    @Test
    fun `an unknown session is answered with a 404 that names it`() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()

        client.prepareGet("/issuer2/sessions/550e8400-e29b-41d4-a716-446655440000/events").execute { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.contentType()?.match(ContentType.Text.EventStream) != true, "${response.contentType()}")
            assertContains(response.bodyAsText(), "Issuance session not found: 550e8400-e29b-41d4-a716-446655440000")
        }
    }

    @Test
    fun `a known session still opens the event stream`() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE)

        client.prepareGet("/issuer2/sessions/${offer.offerId}/events").execute { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.contentType()?.match(ContentType.Text.EventStream) == true, "${response.contentType()}")
            assertEquals("data: {}", response.bodyAsChannel().readUTF8Line())
        }
    }
}
