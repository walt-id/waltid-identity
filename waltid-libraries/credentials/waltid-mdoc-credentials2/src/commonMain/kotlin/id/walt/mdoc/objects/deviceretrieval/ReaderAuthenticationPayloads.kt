@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.deviceretrieval

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.SessionTranscript
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer

/** Builds the detached payloads defined by ISO/IEC 18013-5 reader authentication. */
object ReaderAuthenticationPayloads {

    fun forDocument(
        sessionTranscript: SessionTranscript,
        itemsRequest: ByteStringWrapper<ItemsRequest>,
    ): ByteArray = tagEncodedCbor(
        concatenate(
            byteArrayOf(0x83.toByte()),
            coseCompliantCbor.encodeToByteArray(String.serializer(), "ReaderAuthentication"),
            coseCompliantCbor.encodeToByteArray(SessionTranscript.serializer(), sessionTranscript),
            tagEncodedCbor(itemsRequest.encodedItemsRequest()),
        )
    )

    fun forAllDocuments(
        sessionTranscript: SessionTranscript,
        itemsRequests: List<ByteStringWrapper<ItemsRequest>>,
        deviceRequestInfo: ByteStringWrapper<DeviceRequestInfo>?,
    ): ByteArray {
        require(itemsRequests.isNotEmpty()) { "ReaderAuthenticationAll requires a document request" }
        return tagEncodedCbor(
            concatenate(
                byteArrayOf(0x84.toByte()),
                coseCompliantCbor.encodeToByteArray(String.serializer(), "ReaderAuthenticationAll"),
                coseCompliantCbor.encodeToByteArray(SessionTranscript.serializer(), sessionTranscript),
                concatenate(
                    cborArrayHeader(itemsRequests.size),
                    *itemsRequests.map { tagEncodedCbor(it.encodedItemsRequest()) }.toTypedArray(),
                ),
                deviceRequestInfo?.let { tagEncodedCbor(it.encodedDeviceRequestInfo()) }
                    ?: byteArrayOf(0xf6.toByte()),
            )
        )
    }

    private fun ByteStringWrapper<ItemsRequest>.encodedItemsRequest(): ByteArray =
        serialized.takeIf { it.isNotEmpty() }
            ?: coseCompliantCbor.encodeToByteArray(ItemsRequest.serializer(), value)

    private fun ByteStringWrapper<DeviceRequestInfo>.encodedDeviceRequestInfo(): ByteArray =
        serialized.takeIf { it.isNotEmpty() }
            ?: coseCompliantCbor.encodeToByteArray(DeviceRequestInfo.serializer(), value)

    private fun tagEncodedCbor(encoded: ByteArray): ByteArray =
        byteArrayOf(0xd8.toByte(), 0x18) +
            coseCompliantCbor.encodeToByteArray(ByteArraySerializer(), encoded)

    private fun cborArrayHeader(size: Int): ByteArray = when {
        size < 24 -> byteArrayOf((0x80 + size).toByte())
        size <= 0xff -> byteArrayOf(0x98.toByte(), size.toByte())
        else -> throw IllegalArgumentException("Too many document requests")
    }

    private fun concatenate(vararg parts: ByteArray): ByteArray =
        parts.fold(ByteArray(0)) { result, bytes -> result + bytes }
}
