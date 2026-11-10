package id.walt.sdjwt.metadata.type

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class TypeMetadataResolverTest {
    private val leaf = "https://issuer.example/leaf"
    private fun resolver(documents: Map<String, ByteArray> = TypeMetadataFixture.documents, depth: Int = 8) =
        TypeMetadataResolver({ documents[it] ?: error("unreachable") }, maxDepth = depth)

    @Test fun resolvesThreeLevelsAndReplacesDisplayWithoutLosingClaimRestrictions() = runTest {
        val resolved = resolver().resolve(leaf, TypeMetadataFixture.LEAF_PIN, requireParentIntegrity = true)
        assertEquals(listOf(leaf, "https://types.example/sca-user", "https://types.example/sca"), resolved.types.toList())
        assertEquals("Bank attestation", resolved.effective.getValue("display").jsonArray.single().jsonObject["name"]?.jsonPrimitive?.content)
        val claim = resolved.effective.getValue("claims").jsonArray.single().jsonObject
        assertEquals(JsonPrimitive(true), claim["mandatory"])
        assertEquals(JsonPrimitive("always"), claim["sd"])
        assertEquals(JsonPrimitive("IBAN"), claim["display"]?.jsonArray?.single()?.jsonObject?.get("label"))
        assertEquals(1, resolved.schemas.size)
        assertEquals("Bank attestation", resolved.display.single().name)
        assertEquals(listOf(MetadataClaimPathComponent.Name("accounts"), MetadataClaimPathComponent.AnyIndex, MetadataClaimPathComponent.Name("iban")), resolved.claims.single().path)
        assertEquals(ClaimSdMetadata.Always, resolved.claims.single().sd)
        assertFalse("extends" in resolved.effective)
        val snapshot = TypeMetadataResolver.snapshotDocuments(resolved.snapshot())
        assertEquals(resolved.effective, resolver(snapshot).resolve(leaf, TypeMetadataFixture.LEAF_PIN, true).effective)
    }

    @Test fun sriUsesOriginalBytesAndStrongestSupportedAlgorithm() {
        val bytes = TypeMetadataFixture.ROOT.encodeToByteArray()
        for (pin in listOf(TypeMetadataFixture.ROOT_SHA384, TypeMetadataFixture.ROOT_SHA512)) {
            MetadataIntegrity.verify(bytes, pin)
            MetadataIntegrity.verify(bytes, pin.replace('+', '-').replace('/', '_').trimEnd('='))
        }
        val weak = MetadataIntegrity.sha256(bytes)
        val wrongStrong = TypeMetadataFixture.ROOT_SHA512.replaceRange(7, 8, "A")
        assertEquals(TypeMetadataFailure.INTEGRITY_MISMATCH,
            assertFailsWith<TypeMetadataException> { MetadataIntegrity.verify(bytes, "$weak $wrongStrong") }.reason)
        assertFailsWith<TypeMetadataException> { MetadataIntegrity.verify((TypeMetadataFixture.ROOT + " ").encodeToByteArray(), TypeMetadataFixture.ROOT_SHA384) }
        assertFailsWith<TypeMetadataException> { MetadataIntegrity.verify(bytes, "sha1-unknown") }
    }

    @Test fun rejectsTamperingAtEveryHopAndWrongDocumentIdentity() = runTest {
        for ((uri, bytes) in TypeMetadataFixture.documents) {
            val changed = TypeMetadataFixture.documents + (uri to (bytes.decodeToString() + " ").encodeToByteArray())
            assertEquals(TypeMetadataFailure.INTEGRITY_MISMATCH,
                assertFailsWith<TypeMetadataException> { resolver(changed).resolve(leaf, TypeMetadataFixture.LEAF_PIN) }.reason)
        }
        val wrong = mapOf(leaf to "{\"vct\":\"https://wrong.example/type\"}".encodeToByteArray())
        assertEquals(TypeMetadataFailure.INVALID_METADATA, assertFailsWith<TypeMetadataException> { resolver(wrong).resolve(leaf) }.reason)
    }

    @Test fun distinguishesCyclesDepthUnavailableAndCancellation() = runTest {
        val cycle = mapOf(leaf to "{\"vct\":\"$leaf\",\"extends\":\"$leaf\"}".encodeToByteArray())
        assertEquals(TypeMetadataFailure.CYCLE, assertFailsWith<TypeMetadataException> { resolver(cycle).resolve(leaf) }.reason)
        assertEquals(TypeMetadataFailure.LIMIT_EXCEEDED, assertFailsWith<TypeMetadataException> { resolver(depth = 2).resolve(leaf) }.reason)
        assertEquals(3, resolver(depth = 3).resolve(leaf).documents.size)
        assertEquals(TypeMetadataFailure.UNAVAILABLE, assertFailsWith<TypeMetadataException> { resolver(emptyMap()).resolve(leaf) }.reason)
        assertFailsWith<CancellationException> { TypeMetadataResolver({ throw CancellationException() }).resolve(leaf) }
    }

    @Test fun parentPinsAreOptionalGenericallyButRequiredByStrictProfile() = runTest {
        val unpinned = TypeMetadataFixture.LEAF.replace(Regex(",\"extends#integrity\":\"[^\"]+\""), "")
        val documents = TypeMetadataFixture.documents + (leaf to unpinned.encodeToByteArray())
        assertEquals(3, resolver(documents).resolve(leaf).documents.size)
        assertEquals(TypeMetadataFailure.INVALID_METADATA, assertFailsWith<TypeMetadataException> { resolver(documents).resolve(leaf, requireParentIntegrity = true) }.reason)
    }

    @Test fun rejectsWeakeningAndKeepsNumericIndicesDistinctFromStrings() = runTest {
        val parent = "https://types.example/base"
        suspend fun resolve(childClaims: String, parentClaims: String) = resolver(mapOf(
            parent to "{\"vct\":\"$parent\",\"claims\":$parentClaims}".encodeToByteArray(),
            leaf to "{\"vct\":\"$leaf\",\"extends\":\"$parent\",\"claims\":$childClaims}".encodeToByteArray(),
        )).resolve(leaf)
        for ((old, new) in listOf("\"sd\":\"always\"" to "\"sd\":\"allowed\"", "\"mandatory\":true" to "\"mandatory\":false")) {
            assertFailsWith<TypeMetadataException> { resolve("[{\"path\":[\"x\"],$new}]", "[{\"path\":[\"x\"],$old}]") }
        }
        val result = resolve("[{\"path\":[\"x\",0]}]", "[{\"path\":[\"x\",\"0\"]}]")
        assertEquals(2, result.effective.getValue("claims").jsonArray.size)
    }

    @Test fun publisherFillsPinsAndProducesReproducibleBytes() = runTest {
        val child = Json.parseToJsonElement(TypeMetadataFixture.LEAF).jsonObject
        val configured = mapOf(leaf to JsonObject(child - "extends#integrity"))
        val publication = TypeMetadataPublisher { TypeMetadataFixture.documents.getValue(it) }.publish(configured).getValue(leaf)
        val second = TypeMetadataPublisher { TypeMetadataFixture.documents.getValue(it) }.publish(configured).getValue(leaf)
        assertContentEquals(publication.bytes(), second.bytes())
        MetadataIntegrity.verify(publication.bytes(), publication.integrity)
        assertEquals(3, resolver(TypeMetadataFixture.documents + (leaf to publication.bytes())).resolve(leaf, publication.integrity).documents.size)
        val external = PublishedTypeMetadata.fromRetrieved("https://types.example/sca", TypeMetadataFixture.ROOT.encodeToByteArray())
        assertEquals(Json.parseToJsonElement(TypeMetadataFixture.PARENT).jsonObject.getValue("extends#integrity").jsonPrimitive.content, external.integrity)
        val spaced = (TypeMetadataFixture.ROOT + " ").encodeToByteArray()
        assertContentEquals(spaced, PublishedTypeMetadata.fromRetrieved("https://types.example/sca", spaced).bytes())
    }
}
