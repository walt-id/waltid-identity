@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.iso18013.annexc

import id.walt.cose.Cose
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.ItemRequest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AnnexCRequestBuilderTest {

    @Test
    fun `build returns base64url CBOR for DeviceRequest and EncryptionInfo`() {
        val nonce = ByteArray(16) { it.toByte() }
        val recipientKey = CoseKey(
            kty = Cose.KeyTypes.EC2,
            crv = Cose.EllipticCurves.P_256,
            x = ByteArray(32) { (it + 1).toByte() },
            y = ByteArray(32) { (it + 2).toByte() },
        )

        data class Case(
            val requestedElements: Map<String, List<String>>,
            val intentToRetain: Boolean,
            val expectedNamespaces: Map<String, List<ItemRequest>>,
        )
        val cases = listOf(
            Case(
                requestedElements = mapOf("org.iso.18013.5.1" to listOf("age_over_18")),
                intentToRetain = false,
                expectedNamespaces = mapOf("org.iso.18013.5.1" to listOf(ItemRequest("age_over_18", false))),
            ),
            Case(
                requestedElements = mapOf(
                    "org.iso.18013.5.1" to listOf("age_over_18", "age_over_18", "given_name"),
                    "empty" to emptyList(),
                ),
                intentToRetain = true,
                expectedNamespaces = mapOf(
                    "org.iso.18013.5.1" to listOf(ItemRequest("age_over_18", true), ItemRequest("given_name", true)),
                ),
            ),
        )

        for (case in cases) {
            val req = AnnexCRequestBuilder.build(
                docType = "org.iso.18013.5.1.mDL",
                requestedElements = case.requestedElements,
                nonce = nonce,
                recipientPublicKey = recipientKey,
                intentToRetain = case.intentToRetain,
            )

            val deviceRequestCbor = req.deviceRequestB64.base64UrlDecode()
            val encryptionInfoCbor = req.encryptionInfoB64.base64UrlDecode()

            val deviceRequest = coseCompliantCbor.decodeFromByteArray(DeviceRequest.serializer(), deviceRequestCbor)
            assertEquals("1.0", deviceRequest.version)
            assertEquals(1, deviceRequest.docRequests.size)
            val itemsRequest = deviceRequest.docRequests.single().itemsRequest.value
            assertEquals("org.iso.18013.5.1.mDL", itemsRequest.docType)
            assertEquals(case.expectedNamespaces, itemsRequest.namespaces.mapValues { it.value.entries })

            val encryptionInfo = coseCompliantCbor.decodeFromByteArray(DCAPIEncryptionInfo.serializer(), encryptionInfoCbor)
            assertEquals("dcapi", encryptionInfo.type)
            assertContentEquals(nonce, encryptionInfo.encryptionParameters.nonce)
            assertEquals(recipientKey, encryptionInfo.encryptionParameters.recipientPublicKey)
        }
    }
}
