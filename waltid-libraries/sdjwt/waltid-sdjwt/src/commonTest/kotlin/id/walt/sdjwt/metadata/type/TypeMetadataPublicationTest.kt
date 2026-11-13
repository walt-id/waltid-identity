package id.walt.sdjwt.metadata.type

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class TypeMetadataPublicationTest {
    @Test fun retainedPublicationsKeepOriginalPinsAcrossRestartAndRetainRemovedVersions() = runTest {
        val vct = "https://issuer.example/type/1"
        val parent = "https://types.example/base"
        var body = """{"vct":"$parent","name":"Original"}""".encodeToByteArray()
        var calls = 0
        val publisher = TypeMetadataPublisher { calls++; body }
        val source = buildJsonObject { put("vct", vct); put("extends", parent) }
        val first = publisher.retain(mapOf(vct to source), emptyMap())
        val original = first.getValue(vct).published
        val reopened = first.mapValues { TypeMetadataPublication.restore(it.value.snapshot()) }
        body = """{"vct":"$parent","name":"Changed upstream"}""".encodeToByteArray()
        val before = calls
        val retained = publisher.retain(emptyMap(), reopened)
        assertContentEquals(original.bytes(), retained.getValue(vct).published.bytes())
        assertEquals(original.integrity, retained.getValue(vct).published.integrity)
        assertEquals(before, calls)
        assertFailsWith<TypeMetadataException> {
            publisher.retain(mapOf(vct to JsonObject(source + ("name" to JsonPrimitive("Replacement")))), reopened)
        }
        val next = "https://issuer.example/type/2"
        val updated = publisher.retain(mapOf(next to buildJsonObject { put("vct", next); put("extends", parent) }), reopened)
        assertEquals(setOf(vct, next), updated.keys)
        assertEquals(before, calls)
        assertEquals(original.metadata["extends#integrity"], updated.getValue(next).published.metadata["extends#integrity"])
    }

    @Test fun aHostedParentChainIsRetainedWithoutFetchingTheIssuersOwnEndpoints() = runTest {
        val root = "https://issuer.example/base"
        val leaf = "https://issuer.example/leaf"
        val publications = TypeMetadataPublisher { error("Hosted parents must resolve through the catalogue") }.retain(
            mapOf(
                leaf to buildJsonObject { put("vct", leaf); put("extends", root) },
                root to buildJsonObject { put("vct", root); put("name", "Base") },
            ), emptyMap(),
        )
        assertEquals(listOf(leaf, root), publications.getValue(leaf).resolved.types.toList())
        val reopened = publications.mapValues { TypeMetadataPublication.restore(it.value.snapshot()) }
        assertContentEquals(publications.getValue(leaf).published.bytes(), reopened.getValue(leaf).published.bytes())
    }

    @Test fun retainedSnapshotRechecksOriginalBytesAndSourceIdentity() = runTest {
        val vct = "urn:example:identity"
        val publication = TypeMetadataPublisher { error("No external source") }.retain(
            mapOf(vct to buildJsonObject { put("vct", vct); put("name", "Identity") }), emptyMap(),
        ).getValue(vct)
        val snapshot = publication.snapshot()
        val altered = JsonObject(snapshot + ("source" to buildJsonObject { put("vct", vct); put("name", "Changed") }))
        assertFailsWith<IllegalArgumentException> { TypeMetadataPublication.restore(altered) }
        assertContentEquals(publication.published.bytes(), TypeMetadataPublication.restore(snapshot).published.bytes())
    }
}
