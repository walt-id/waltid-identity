@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.iso18013.annexc

import id.walt.cose.Cose
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
import id.walt.mdoc.objects.dcapi.DCAPIHandover
import id.walt.mdoc.objects.sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.encodeToByteArray
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
    fun `decryptToDeviceResponse roundtrips HPKE base mode P-256`() {
        val hpke = HPKE(
            HPKE.mode_base,
            HPKE.kem_P256_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM128
        )

        val recipientKeyPair = hpke.generatePrivateKey()
        val recipientPublicKeyBytes = hpke.serializePublicKey(recipientKeyPair.public)
        val recipientPrivateKeyBytes = hpke.serializePrivateKey(recipientKeyPair.private)

        val recipientPublicCoseKey = CoseKey(
            kty = Cose.KeyTypes.EC2,
            crv = Cose.EllipticCurves.P_256,
            x = recipientPublicKeyBytes.copyOfRange(1, 33),
            y = recipientPublicKeyBytes.copyOfRange(33, 65),
        )

        val nonce = ByteArray(16) { it.toByte() }
        val encryptionInfoCbor = coseCompliantCbor.encodeToByteArray(
            id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo.serializer(),
            AnnexCRequestBuilder.buildEncryptionInfo(nonce, recipientPublicCoseKey)
        )
        val encryptionInfoB64 = Base64UrlNoPad.encode(encryptionInfoCbor)

        val origin = "https://verifier.example"
        val info = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin)

        val plaintext = ByteArray(128) { (it + 1).toByte() }
        val sealed = hpke.seal(
            recipientKeyPair.public,
            info,
            byteArrayOf(),
            plaintext,
            null,
            null,
            null
        )
        val cipherText = sealed[0]
        val enc = sealed[1]

        val encryptedResponseCbor = coseCompliantCbor.encodeToByteArray(
            AnnexCEncryptedResponse.serializer(),
            AnnexCEncryptedResponse(
                type = "dcapi",
                response = AnnexCEncryptedResponseData(enc = enc, cipherText = cipherText)
            )
        )
        val encryptedResponseB64 = Base64UrlNoPad.encode(encryptedResponseCbor)

        val decrypted = AnnexCResponseVerifierJvm.decryptToDeviceResponse(
            encryptedResponseB64 = encryptedResponseB64,
            encryptionInfoB64 = encryptionInfoB64,
            origin = origin,
            recipientPrivateKey = recipientPrivateKeyBytes
        )
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `decryptToDeviceResponse rejects skR not matching pkR`() {
        val hpke = HPKE(
            HPKE.mode_base,
            HPKE.kem_P256_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM128
        )

        val recipientKeyPair = hpke.generatePrivateKey()
        val wrongKeyPair = hpke.generatePrivateKey()

        val recipientPublicKeyBytes = hpke.serializePublicKey(recipientKeyPair.public)
        val wrongRecipientPrivateKeyBytes = hpke.serializePrivateKey(wrongKeyPair.private)

        val recipientPublicCoseKey = CoseKey(
            kty = Cose.KeyTypes.EC2,
            crv = Cose.EllipticCurves.P_256,
            x = recipientPublicKeyBytes.copyOfRange(1, 33),
            y = recipientPublicKeyBytes.copyOfRange(33, 65),
        )

        val nonce = ByteArray(16) { it.toByte() }
        val encryptionInfoCbor = coseCompliantCbor.encodeToByteArray(
            id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo.serializer(),
            AnnexCRequestBuilder.buildEncryptionInfo(nonce, recipientPublicCoseKey)
        )
        val encryptionInfoB64 = Base64UrlNoPad.encode(encryptionInfoCbor)

        val origin = "https://verifier.example"
        val info = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin)

        val plaintext = byteArrayOf(1, 2, 3)
        val sealed = hpke.seal(
            recipientKeyPair.public,
            info,
            byteArrayOf(),
            plaintext,
            null,
            null,
            null
        )

        val encryptedResponseCbor = coseCompliantCbor.encodeToByteArray(
            AnnexCEncryptedResponse.serializer(),
            AnnexCEncryptedResponse(
                type = "dcapi",
                response = AnnexCEncryptedResponseData(enc = sealed[1], cipherText = sealed[0])
            )
        )
        val encryptedResponseB64 = Base64UrlNoPad.encode(encryptedResponseCbor)

        assertFailsWith<IllegalArgumentException> {
            AnnexCResponseVerifierJvm.decryptToDeviceResponse(
                encryptedResponseB64 = encryptedResponseB64,
                encryptionInfoB64 = encryptionInfoB64,
                origin = origin,
                recipientPrivateKey = wrongRecipientPrivateKeyBytes
            )
        }
    }
}

