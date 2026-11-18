package id.walt.wallet2.handlers

import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft13
import id.walt.sdjwt.metadata.type.TypeMetadataFixture
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeMetadataConsumerSerializationTest {
    @Test
    fun discoversMetadataSerializersFromAnotherModule() {
        val wire = Json.parseToJsonElement(TypeMetadataFixture.LEAF)
        val draft04 = Json.decodeFromJsonElement(SdJwtVcTypeMetadataDraft04.serializer(), wire)
        val draft13 = Json.decodeFromJsonElement(SdJwtVcTypeMetadataDraft13.serializer(), wire)

        assertEquals(wire, Json.encodeToJsonElement(serializer<SdJwtVcTypeMetadataDraft04>(), draft04))
        assertEquals(wire, Json.encodeToJsonElement(serializer<SdJwtVcTypeMetadataDraft13>(), draft13))

        val envelope = MetadataEnvelope(draft04, draft13)
        val expected = buildJsonObject {
            put("draft04", wire)
            put("draft13", wire)
        }
        assertEquals(expected, Json.encodeToJsonElement(MetadataEnvelope.serializer(), envelope))
        assertEquals(envelope, Json.decodeFromJsonElement(MetadataEnvelope.serializer(), expected))
    }

    @Serializable
    private data class MetadataEnvelope(
        val draft04: SdJwtVcTypeMetadataDraft04,
        val draft13: SdJwtVcTypeMetadataDraft13,
    )
}
