package id.walt.issuer2.openid4vci

import id.walt.commons.persistence.InMemoryPersistence
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.service.openid4vci.IssuerTypeMetadataCatalogue
import id.walt.sdjwt.metadata.type.MetadataIntegrity
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import id.walt.sdjwt.metadata.type.TypeMetadataException
import id.walt.webdatafetching.WebDataFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration

class IssuerTypeMetadataCatalogueTest {
    private val base = "https://issuer.example/tenant"
    private val vct = "$base/openid4vci/identity_type"
    private val parent = "https://types.example/person/v1"

    @Test
    fun `hosted origin variants preserve exact VCT and retained slug lookup without self fetch`() = runBlocking {
        for (type in listOf(vct, vct.replace("https:", "HTTPS:"), vct.replace("issuer.example", "ISSUER.EXAMPLE:443"))) {
            val store = InMemoryPersistence<String>("publications", Duration.INFINITE)
            fun catalogue(config: Issuer2MetadataConfig) = IssuerTypeMetadataCatalogue(
                Issuer2ServiceConfig(baseUrl = base), config, store,
                fetcher { error("Hosted metadata must not fetch itself") },
            )
            val published = catalogue(config(SdJwtVcTypeMetadataDraft04(name = "Identity"), type)).use { current ->
                val publication = current.publication("identity_type")
                assertEquals(type, publication.vct)
                current.resolve(current.credentialConfigurations.getValue("identity_config"))!!.use { context ->
                    assertContentEquals(publication.bytes(), context.publication.bytes())
                    MetadataIntegrity.verify(publication.bytes(), context.publication.integrity)
                }
                publication
            }
            catalogue(Issuer2MetadataConfig()).use { removed ->
                assertContentEquals(published.bytes(), removed.publication("identity_type").bytes())
            }
        }
    }

    @Test
    fun `restart retains original parent bytes and removed configurations remain published`() = runBlocking {
        val store = InMemoryPersistence<String>("publications", Duration.INFINITE)
        val reads = AtomicInteger()
        val source = SdJwtVcTypeMetadataDraft04(name = "Identity", extends = parent)
        val originalParent = """{ "vct": "$parent", "name": "Person" }""".encodeToByteArray()
        fun catalogue(config: Issuer2MetadataConfig, available: Boolean) = IssuerTypeMetadataCatalogue(
            Issuer2ServiceConfig(baseUrl = base), config, store, fetcher {
                reads.incrementAndGet()
                check(available) { "Parent is offline after restart" }
                originalParent
            },
        )
        val original = catalogue(config(source), true).use { it.publication("identity_type") }
        assertEquals(1, reads.get())
        MetadataIntegrity.verify(originalParent, original.metadata.getValue("extends#integrity").jsonPrimitive.content)
        catalogue(config(source), false).use { restarted ->
            assertContentEquals(original.bytes(), restarted.publication("identity_type").bytes())
            restarted.resolve(restarted.credentialConfigurations.getValue("identity_config"))!!.use { context ->
                assertContentEquals(original.bytes(), context.publication.bytes())
                assertContentEquals(originalParent, context.resolved.documents.last().bytes())
            }
        }
        catalogue(Issuer2MetadataConfig(), false).use { removed ->
            assertContentEquals(original.bytes(), removed.publication("identity_type").bytes())
        }
        assertEquals(1, reads.get(), "Retained chains must not depend on the parent remaining online")
        catalogue(config(source.copy(name = "Changed")), false).use { changed ->
            assertFailsWith<TypeMetadataException> { changed.publications() }
        }
        assertEquals(1, store.listAllKeys().size)
    }

    @Test
    fun `concurrent conflicting publication has one immutable winner`() = runBlocking {
        val store = InMemoryPersistence<String>("publications", Duration.INFINITE)
        val readers = AtomicInteger()
        val bothReading = CompletableDeferred<Unit>()
        val catalogues = listOf("One", "Two").map { name ->
            IssuerTypeMetadataCatalogue(Issuer2ServiceConfig(baseUrl = base),
                config(SdJwtVcTypeMetadataDraft04(name = name, extends = parent)), store, fetcher {
                    if (readers.incrementAndGet() == 2) bothReading.complete(Unit)
                    bothReading.await()
                    """{"vct":"$parent"}""".encodeToByteArray()
                })
        }
        try {
            val outcomes = catalogues.map { catalogue -> async { runCatching { catalogue.publication("identity_type") } } }.awaitAll()
            val winner = outcomes.single { it.isSuccess }.getOrThrow()
            assertIs<TypeMetadataException>(outcomes.single { it.isFailure }.exceptionOrNull())
            assertEquals(1, store.listAllKeys().size)
            val retained = catalogues[outcomes.indexOfFirst { it.isSuccess }]
            assertContentEquals(winner.bytes(), retained.publication("identity_type").bytes())
        } finally { catalogues.forEach { it.close() } }
    }

    @Test
    fun `shared hosted type and issuer scopes do not depend on configuration ids`() = runBlocking {
        val store = InMemoryPersistence<String>("publications", Duration.INFINITE)
        val source = SdJwtVcTypeMetadataDraft04(name = "Identity")
        val shared = config(source).let { config -> config.copy(
            credentialConfigurations = config.credentialConfigurations + ("second_config" to config.credentialConfigurations.getValue("identity_config")),
            sdJwtVcTypeMetadataConfiguration = config.sdJwtVcTypeMetadataConfiguration + ("second_config" to source),
        ) }
        IssuerTypeMetadataCatalogue(Issuer2ServiceConfig(baseUrl = base), shared, store, fetcher { error("Hosted type fetched itself") }).use {
            assertEquals(setOf(vct), it.publications().keys)
        }
        val conflictingAlias = config(source, vct.replace("issuer.example", "issuer.example:443"))
        IssuerTypeMetadataCatalogue(Issuer2ServiceConfig(baseUrl = base), conflictingAlias, store,
            fetcher { error("Hosted type fetched itself") }).use {
            assertFailsWith<IllegalArgumentException> { it.publications() }
        }
        assertEquals(1, store.listAllKeys().size, "An equivalent URL must not replace or shadow a retained VCT")
        val otherBase = "https://other.example/tenant"
        val otherVct = "$otherBase/openid4vci/identity_type"
        IssuerTypeMetadataCatalogue(Issuer2ServiceConfig(baseUrl = otherBase), config(source, otherVct), store,
            fetcher { error("Hosted type fetched itself") }).use {
            assertEquals(setOf(otherVct), it.publications().keys)
        }
        assertEquals(2, store.listAllKeys().size)
    }

    private fun config(source: SdJwtVcTypeMetadataDraft04, type: String = vct) = Issuer2MetadataConfig(
        credentialConfigurations = mapOf("identity_config" to buildJsonObject {
            put("format", "dc+sd-jwt"); put("vct", type)
        }), sdJwtVcTypeMetadataConfiguration = mapOf("identity_config" to source),
    )

    private fun fetcher(load: suspend () -> ByteArray) = WebDataFetcher.wrapping(
        HttpClient(MockEngine { request ->
            assertEquals(parent, request.url.toString())
            respond(load())
        }), "issuer-publication-test",
    )
}
