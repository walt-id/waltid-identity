@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.digitalcredentials

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toEncodedJwk
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.hpke.Hpke
import id.walt.crypto2.keys.HpkeCiphertext
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.handover.AnnexCDcapiHandoverInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborObjectAsArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** HPKE-seals the `DeviceResponse` to the reader's recipient key and wraps it for the platform. */
internal suspend fun encryptAnnexCDeviceResponse(
    documents: List<Document>,
    encryptionInfo: DCAPIEncryptionInfo,
    transcript: SessionTranscript,
): DigitalCredentialResponse {
    val plaintext = coseCompliantCbor.encodeToByteArray(
        DeviceResponse.serializer(),
        DeviceResponse(version = "1.0", documents = documents.toTypedArray(), status = 0u),
    )
    val ciphertext = encryptAnnexCHpke(
        recipientPublicKey = encryptionInfo.encryptionParameters.recipientPublicKey,
        plaintext = plaintext,
        info = AnnexCDcapiHandoverInfo.hpkeInfo(transcript),
    )
    val responseBase64Url = coseCompliantCbor.encodeToByteArray(
        AnnexCEncryptedResponse.serializer(),
        AnnexCEncryptedResponse(
            type = "dcapi",
            response = AnnexCEncryptedResponseData(
                enc = ciphertext.encapsulatedKey.toByteArray(),
                cipherText = ciphertext.ciphertext.toByteArray(),
            ),
        ),
    ).encodeToBase64Url()
    return DigitalCredentialResponse(
        protocol = DigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
        dataJson = buildJsonObject {
            put(
                "response",
                JsonPrimitive(responseBase64Url)
            )
        }.toString(),
    )
}

internal suspend fun encryptAnnexCHpke(
    recipientPublicKey: CoseKey,
    plaintext: ByteArray,
    info: ByteArray,
): HpkeCiphertext = Hpke.sealBase(
    recipientPublicKey = recipientPublicKey.toEncodedJwk(),
    plaintext = plaintext,
    info = info,
)

@Serializable
@CborObjectAsArray
private data class AnnexCEncryptedResponse(
    val type: String,
    val response: AnnexCEncryptedResponseData,
)

@Serializable
private data class AnnexCEncryptedResponseData(
    @ByteString val enc: ByteArray,
    @ByteString val cipherText: ByteArray,
)
