package id.walt.mdoc.parser

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.matchesBase64Url
import id.walt.crypto.utils.HexUtils.matchesHex
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.Document
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray

object MdocParser {

    @OptIn(ExperimentalSerializationApi::class)
    fun parseToDocument(signed: String): Document {
        val isHex = signed.matchesHex()
        val isBase64 = signed.matchesBase64Url()

        require(isHex || isBase64) { "Signed is neither hex nor base64" }

        val signedBytes = if (isHex) signed.hexToByteArray() else signed.decodeFromBase64Url()

        val document = runCatching {
            val deviceResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(signedBytes)
            deviceResponse.documents?.firstOrNull() ?: throw IllegalArgumentException("Mdoc document not found in DeviceResponse")
        }.recoverCatching {
            coseCompliantCbor.decodeFromByteArray<Document>(signedBytes)
        }.getOrThrow()

        return document
    }

}
