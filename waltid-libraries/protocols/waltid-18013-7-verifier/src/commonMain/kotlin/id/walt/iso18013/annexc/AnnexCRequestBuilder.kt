@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.iso18013.annexc

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionParameters
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DocRequest
import id.walt.mdoc.objects.deviceretrieval.ItemRequest
import id.walt.mdoc.objects.deviceretrieval.ItemsRequest
import id.walt.mdoc.objects.deviceretrieval.ItemsRequestList

data class AnnexCRequest(
    val deviceRequestB64: String,
    val encryptionInfoB64: String,
)

object AnnexCRequestBuilder {
    private const val DCAPI = "dcapi"
    private const val DEVICE_REQUEST_VERSION = "1.0"

    fun build(
        docType: String,
        requestedElements: Map<String, List<String>>,
        nonce: ByteArray,
        recipientPublicKey: CoseKey,
        intentToRetain: Boolean = false,
    ): AnnexCRequest {
        val deviceRequestCbor = coseCompliantCbor.encodeToByteArray(
            DeviceRequest.serializer(),
            buildDeviceRequest(docType, requestedElements, intentToRetain)
        )
        val encryptionInfoCbor = coseCompliantCbor.encodeToByteArray(
            DCAPIEncryptionInfo.serializer(),
            buildEncryptionInfo(nonce, recipientPublicKey)
        )

        return AnnexCRequest(
            deviceRequestB64 = Base64UrlNoPad.encode(deviceRequestCbor),
            encryptionInfoB64 = Base64UrlNoPad.encode(encryptionInfoCbor),
        )
    }

    fun buildDeviceRequest(
        docType: String,
        requestedElements: Map<String, List<String>>,
        intentToRetain: Boolean = false,
    ): DeviceRequest {
        val namespaces = requestedElements
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, elems) ->
                ItemsRequestList(elems.distinct().map { ItemRequest(it, intentToRetain) })
            }

        val itemsRequest = ItemsRequest(
            docType = docType,
            namespaces = namespaces,
            requestInfo = null,
        )

        val docRequest = DocRequest(
            itemsRequest = ByteStringWrapper(itemsRequest),
            readerAuth = null,
        )

        return DeviceRequest(
            version = DEVICE_REQUEST_VERSION,
            docRequests = listOf(docRequest),
            readerAuthAll = null,
        )
    }

    fun buildEncryptionInfo(nonce: ByteArray, recipientPublicKey: CoseKey): DCAPIEncryptionInfo {
        val params = DCAPIEncryptionParameters(
            nonce = nonce,
            recipientPublicKey = recipientPublicKey
        )
        return DCAPIEncryptionInfo(type = DCAPI, encryptionParameters = params)
    }
}
