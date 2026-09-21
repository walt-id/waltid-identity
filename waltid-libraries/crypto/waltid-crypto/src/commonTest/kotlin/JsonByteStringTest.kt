@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JsonUtils.toSerializedJsonElement
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonByteStringTest {
    @Test
    fun preservesEverySignedByteAndItsSerializedJson() {
        val bytes = ByteArray(256) { (it - 128).toByte() }
        val expected = JsonArray(bytes.map { JsonPrimitive(it) })
        val actual = CborByteString(bytes).toSerializedJsonElement()
        assertEquals(expected, actual)
        assertEquals(expected.hashCode(), actual.hashCode())
        assertEquals(expected.toString(), Json.encodeToString(JsonElement.serializer(), actual))
        assertEquals(JsonArray(emptyList()), CborByteString(byteArrayOf()).toJsonElement())
    }

    @Test
    fun conversionOwnsAnImmutableByteSnapshot() {
        val bytes = byteArrayOf(1, -1, 127, -128)
        val actual = CborByteString(bytes).toJsonElement()
        bytes.fill(0)
        assertEquals("[1,-1,127,-128]", actual.toString())
    }
}
