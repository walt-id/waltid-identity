package id.walt.issuer2.openid4vci

import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class Issuer2CredentialCacheControlTest {
    @AfterEach
    fun clearConfig() = clearIssuer2TestEnvironment()

    @Test
    fun `route failures before the shared response writer prohibit caching`() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        for (body in listOf("{", "{}")) {
            val response = client.post("/openid4vci/credential") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertEquals(if (body == "{") HttpStatusCode.BadRequest else HttpStatusCode.Unauthorized, response.status)
            assertEquals("no-store", response.headers["Cache-Control"])
        }
        val metadata = client.post("/openid4vci/nonexistent")
        assertEquals(HttpStatusCode.NotFound, metadata.status)
        assertEquals(null, metadata.headers["Cache-Control"])
    }
}
