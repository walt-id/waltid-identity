@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.elements.IssuerSignedList
import id.walt.mdoc.objects.elements.IssuerSignedListSerializer
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
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

    @Test
    fun `device namespaces preserve profile-neutral CBOR values`() {
        val namespaces = DeviceNameSpaces(
            mapOf(
                "org.example.application" to DeviceSignedItemList(
                    listOf(DeviceSignedItem("authorization_code", CborInteger(7))),
                )
            )
        )

        val encoded = coseCompliantCbor.encodeToByteArray(DeviceNameSpaces.serializer(), namespaces)
        val decoded = coseCompliantCbor.decodeFromByteArray(CborElement.serializer(), encoded) as CborMap
        val application = decoded[CborString("org.example.application")] as CborMap

        assertEquals(CborInteger(7), application[CborString("authorization_code")])
    }
}
