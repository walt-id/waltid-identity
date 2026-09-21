package id.walt.issuer2.openid4vci

import id.walt.commons.web.modules.OpenApiModule
import id.walt.issuer2.testsupport.*
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class Issuer2OpenApiCompatibilityTest {
    @AfterEach
    fun clear() = clearIssuer2TestEnvironment()

    @Test
    fun `generated OpenAPI describes both offer contracts and both public session shapes without a discriminator`() = testApplication {
        application {
            install(OpenApi) { schemas { generator = OpenApiModule.createGenerator() } }
            routing { route("api.json") { openApi() } }
        }
        installIssuer2WithConfigFiles()
        val specification = Json.parseToJsonElement(apiClient().get("/api.json").bodyAsText()).jsonObject
        val schemas = specification.getValue("components").jsonObject.getValue("schemas").jsonObject
        fun resolve(schema: JsonElement): JsonObject = schema.jsonObject.let {
            it["\$ref"]?.jsonPrimitive?.content?.substringAfterLast('/')?.let { name -> schemas.getValue(name).jsonObject } ?: it
        }
        fun body(operation: JsonObject) = operation.getValue("content").jsonObject
            .getValue("application/json").jsonObject.getValue("schema")
        val paths = specification.getValue("paths").jsonObject
        val offer = paths.getValue("/issuer2/credential-offers").jsonObject.getValue("post").jsonObject
        val requests = resolve(body(offer.getValue("requestBody").jsonObject)).getValue("anyOf").jsonArray.map(::resolve)
        assertEquals(2, requests.size)
        assertTrue(requests.any { "profileId" in it.getValue("properties").jsonObject })
        assertTrue(requests.any { "credentials" in it.getValue("properties").jsonObject })
        requests.forEach {
            assertFalse(it.containsKey("discriminator"), it.toString())
            assertFalse(it.getValue("properties").jsonObject.containsKey("type"), it.toString())
        }
        val responses = resolve(body(offer.getValue("responses").jsonObject.getValue("201").jsonObject))
            .getValue("anyOf").jsonArray.map(::resolve)
        assertEquals(1, responses.count { "profileId" in it.getValue("properties").jsonObject })
        assertTrue(responses.none { "credentials" in it.getValue("properties").jsonObject })
        val session = paths.getValue("/issuer2/sessions/{sessionId}").jsonObject.getValue("get").jsonObject
        val sessionShapes = resolve(body(session.getValue("responses").jsonObject.getValue("200").jsonObject))
            .getValue("anyOf").jsonArray.map(::resolve)
        assertTrue(sessionShapes.any { "profileId" in it.getValue("properties").jsonObject })
        assertTrue(sessionShapes.any { "issuanceRequests" in it.getValue("properties").jsonObject })
        assertFalse(specification.toString().contains("CredentialOfferCredentialResponse"))
    }
}
