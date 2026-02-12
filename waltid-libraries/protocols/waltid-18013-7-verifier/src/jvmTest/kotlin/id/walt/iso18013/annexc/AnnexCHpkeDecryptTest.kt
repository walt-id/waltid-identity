@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.iso18013.annexc

import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.objects.dcapi.DCAPIHandover
import id.walt.mdoc.objects.sha256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborArray
import org.bouncycastle.crypto.hpke.HPKE
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnnexCHpkeDecryptTest {

    @Serializable
    @CborArray
    private data class DcApiSessionTranscriptCbor(
        val deviceEngagementBytes: ByteArray? = byteArrayOf(0x00),
        val eReaderKeyBytes: ByteArray? = byteArrayOf(0x00),
        val handover: DCAPIHandover,
    )

    @Test
    fun `hpke info is CBOR(SessionTranscript) with dcapiInfoHash`() {
        val encryptionInfoB64 = "encryptionInfoB64-string"
        val origin = "https://verifier.example"

        val dcapiInfoCbor = AnnexCTranscriptBuilder.encodeDcApiInfo(encryptionInfoB64, origin)
        val dcapiInfoHash = dcapiInfoCbor.sha256()

        val transcript = AnnexCTranscriptBuilder.buildSessionTranscript(encryptionInfoB64, origin)
        val handover = requireNotNull(transcript.dcapiHandover)

        assertEquals(DCAPIHandover.HandoverType.dcapi, handover.type)
        assertContentEquals(dcapiInfoHash, handover.dcapiInfoHash)

        val expectedHpkeInfo = coseCompliantCbor.encodeToByteArray(
            DcApiSessionTranscriptCbor.serializer(),
            DcApiSessionTranscriptCbor(
                deviceEngagementBytes = null,
                eReaderKeyBytes = null,
                handover = DCAPIHandover(
                    type = DCAPIHandover.HandoverType.dcapi,
                    dcapiInfoHash = dcapiInfoHash
                )
            )
        )
        val hpkeInfo = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin)
        assertContentEquals(expectedHpkeInfo, hpkeInfo)
    }

    @Test
    fun `decryptToDeviceResponse roundtrips HPKE base mode P-256`() = runTest {
        val hpke = HPKE(
            HPKE.mode_base,
            HPKE.kem_P256_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM128
        )

        val recipientKeyPair = JWKKey.generate(KeyType.secp256r1)

        val recipientPublicCoseKey = recipientKeyPair.getCosePublicKey()

        val nonce = ByteArray(16) { it.toByte() }
        val encryptionInfoCbor = coseCompliantCbor.encodeToByteArray(
            id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo.serializer(),
            AnnexCRequestBuilder.buildEncryptionInfo(nonce, recipientPublicCoseKey)
        )
        val encryptionInfoB64 = encryptionInfoCbor.encodeToBase64Url()

        val origin = "https://verifier.example"
        val info = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin)

        val plaintext = ByteArray(128) { (it + 1).toByte() }

        val (enc, cipherText) = recipientKeyPair.encryptHpke(plaintext, info, byteArrayOf())

        val encryptedResponseCbor = coseCompliantCbor.encodeToByteArray(
            AnnexCEncryptedResponse.serializer(),
            AnnexCEncryptedResponse(
                type = "dcapi",
                response = AnnexCEncryptedResponseData(enc = enc, cipherText = cipherText)
            )
        )
        val encryptedResponseB64 = encryptedResponseCbor.encodeToBase64Url()

        val decrypted = AnnexCResponseVerifier.decryptToDeviceResponse(
            encryptedResponseB64 = encryptedResponseB64,
            encryptionInfoB64 = encryptionInfoB64,
            origin = origin,
            recipientPrivateKey = recipientKeyPair
        )
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `decryptToDeviceResponse rejects skR not matching pkR`() = runTest {
        HPKE(
            HPKE.mode_base,
            HPKE.kem_P256_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM128
        )


        val recipientKeyPair = JWKKey.generate(KeyType.secp256r1)
        val wrongKeyPair = JWKKey.generate(KeyType.secp256r1)

        val recipientPublicCoseKey = recipientKeyPair.getCosePublicKey()

        val nonce = ByteArray(16) { it.toByte() }
        val encryptionInfoCbor = coseCompliantCbor.encodeToByteArray(
            id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo.serializer(),
            AnnexCRequestBuilder.buildEncryptionInfo(nonce, recipientPublicCoseKey)
        )
        val encryptionInfoB64 = encryptionInfoCbor.encodeToBase64Url()

        val origin = "https://verifier.example"
        val info = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin)

        val plaintext = byteArrayOf(1, 2, 3)
        val (enc, cipherText) = recipientKeyPair.encryptHpke(plaintext = plaintext, info = info, aad = byteArrayOf())

        val encryptedResponseCbor = coseCompliantCbor.encodeToByteArray(
            AnnexCEncryptedResponse.serializer(),
            AnnexCEncryptedResponse(
                type = "dcapi",
                response = AnnexCEncryptedResponseData(enc = enc, cipherText = cipherText)
            )
        )
        val encryptedResponseB64 = encryptedResponseCbor.encodeToBase64Url()

        assertFailsWith<Exception> {
            AnnexCResponseVerifier.decryptToDeviceResponse(
                encryptedResponseB64 = encryptedResponseB64,
                encryptionInfoB64 = encryptionInfoB64,
                origin = origin,
                recipientPrivateKey = wrongKeyPair
            )
        }
    }
}

