@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.elements.IssuerSignedList
import id.walt.mdoc.objects.elements.IssuerSignedListSerializer
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ByteStringWrapperSerializationTest {
    @Test
    fun `serialization preserves original wrapped CBOR bytes`() {
        val originalBytes = byteArrayOf(0xbf.toByte(), 0xff.toByte())
        val wrapper = ByteStringWrapper(DeviceNameSpaces(emptyMap()), originalBytes)
        val encoded = coseCompliantCbor.encodeToByteArray(wrapper)

        assertContentEquals(originalBytes, coseCompliantCbor.decodeFromByteArray<ByteArray>(encoded))
    }

    @Test
    fun `issuer signed list serialization reuses received item bytes`() {
        val decodedValue = IssuerSignedItem(1u, ByteArray(16) { 1 }, "decoded", CborString("decoded"))
        val transmittedValue = IssuerSignedItem(2u, ByteArray(16) { 2 }, "transmitted", CborString("transmitted"))
        val transmittedBytes = coseCompliantCbor.encodeToByteArray(IssuerSignedItem.serializer(), transmittedValue)
        val list = IssuerSignedList(listOf(ByteStringWrapper(decodedValue, transmittedBytes)))

        val encoded = coseCompliantCbor.encodeToByteArray(IssuerSignedListSerializer("namespace"), list)
        val decoded = coseCompliantCbor.decodeFromByteArray(IssuerSignedListSerializer("namespace"), encoded)

        assertContentEquals(transmittedBytes, decoded.entries.single().serialized)
        assertEquals("transmitted", decoded.entries.single().value.elementIdentifier)
    }
}
