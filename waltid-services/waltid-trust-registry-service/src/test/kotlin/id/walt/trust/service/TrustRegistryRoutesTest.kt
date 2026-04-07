package id.walt.trust.service

import id.walt.trust.model.*
import id.walt.trust.service.config.TrustRegistryConfig
import id.walt.trust.service.routes.LoadSourceRequest
import id.walt.trust.service.routes.ResolveCertificateRequest
import id.walt.trust.service.routes.ResolveProviderRequest
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.*

class TrustRegistryRoutesTest {

    private fun loadResource(name: String): String =
        this::class.java.classLoader.getResource(name)?.readText()
            ?: error("Test resource not found: $name")

    private fun ApplicationTestBuilder.configuredClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            TrustRegistryConfig.init()
            trustRegistryModule(withPlugins = true)
        }
        block()
    }

    // ---------------------------------------------------------------------------
    // Source loading
    // ---------------------------------------------------------------------------

    @Test
    fun `POST sources load -- LoTE JSON`() = testApp {
        val client = configuredClient()
        val json = loadResource("sample-lote-wallet-providers.json")

        val response = client.post("/trust-registry/sources/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadSourceRequest(sourceId = "test-src", content = json))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.body<RefreshResult>()
        assertTrue(result.success)
        assertEquals(3, result.entitiesLoaded)
    }

    // ---------------------------------------------------------------------------
    // Resolve routes
    // ---------------------------------------------------------------------------

    @Test
    fun `POST resolve certificate -- by SHA-256`() = testApp {
        val client = configuredClient()
        val json = loadResource("sample-lote-wallet-providers.json")

        // Load source first
        client.post("/trust-registry/sources/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadSourceRequest(sourceId = "test-src", content = json))
        }

        // Resolve
        val response = client.post("/trust-registry/resolve/certificate") {
            contentType(ContentType.Application.Json)
            setBody(ResolveCertificateRequest(
                certificateSha256Hex = "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
                instant = "2026-06-01T00:00:00Z"
            ))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decision = response.body<TrustDecision>()
        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals("AT-WALLET-001", decision.matchedEntity?.entityId)
    }

    @Test
    fun `POST resolve provider-id`() = testApp {
        val client = configuredClient()
        val json = loadResource("sample-lote-wallet-providers.json")

        client.post("/trust-registry/sources/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadSourceRequest(sourceId = "test-src", content = json))
        }

        val response = client.post("/trust-registry/resolve/provider-id") {
            contentType(ContentType.Application.Json)
            setBody(ResolveProviderRequest(
                providerId = "AT-WALLET-001",
                instant = "2026-06-01T00:00:00Z"
            ))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decision = response.body<TrustDecision>()
        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
    }

    // ---------------------------------------------------------------------------
    // Entity listing
    // ---------------------------------------------------------------------------

    @Test
    fun `GET entities`() = testApp {
        val client = configuredClient()
        val json = loadResource("sample-lote-wallet-providers.json")

        client.post("/trust-registry/sources/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadSourceRequest(sourceId = "test-src", content = json))
        }

        val response = client.get("/trust-registry/entities")
        assertEquals(HttpStatusCode.OK, response.status)
        val entities = response.body<List<TrustedEntity>>()
        assertEquals(3, entities.size)
    }

    @Test
    fun `GET entities with type filter`() = testApp {
        val client = configuredClient()
        val json = loadResource("sample-lote-wallet-providers.json")

        client.post("/trust-registry/sources/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadSourceRequest(sourceId = "test-src", content = json))
        }

        val response = client.get("/trust-registry/entities?entityType=PID_PROVIDER")
        assertEquals(HttpStatusCode.OK, response.status)
        val entities = response.body<List<TrustedEntity>>()
        assertEquals(1, entities.size)
        assertEquals("AT-PID-001", entities.first().entityId)
    }

    // ---------------------------------------------------------------------------
    // Health & sources
    // ---------------------------------------------------------------------------

    @Test
    fun `GET health`() = testApp {
        val client = configuredClient()
        val json = loadResource("sample-lote-wallet-providers.json")

        client.post("/trust-registry/sources/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadSourceRequest(sourceId = "test-src", content = json))
        }

        val response = client.get("/trust-registry/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val health = response.body<List<TrustSourceHealth>>()
        assertEquals(1, health.size)
        assertEquals(3, health.first().entityCount)
    }

    @Test
    fun `GET sources`() = testApp {
        val client = configuredClient()

        client.post("/trust-registry/sources/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadSourceRequest(
                sourceId = "src-1",
                content = loadResource("sample-lote-wallet-providers.json")
            ))
        }
        client.post("/trust-registry/sources/load") {
            contentType(ContentType.Application.Json)
            setBody(LoadSourceRequest(
                sourceId = "src-2",
                content = loadResource("sample-tl.xml")
            ))
        }

        val response = client.get("/trust-registry/sources")
        assertEquals(HttpStatusCode.OK, response.status)
        val sources = response.body<List<TrustSource>>()
        assertEquals(2, sources.size)
    }
}
