package id.walt.iso18013.annexc

import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo

actual object AnnexCResponseVerifier {
    actual suspend fun decryptToDeviceResponse(
        encryptedResponseB64: String,
        encryptionInfoB64: String,
        origin: String,
        recipientPrivateKey: JWKKey,
    ): ByteArray {
        val encryptedResponseCbor = encryptedResponseB64.base64UrlDecode()
        val encryptedResponse =
            coseCompliantCbor.decodeFromByteArray(AnnexCEncryptedResponse.serializer(), encryptedResponseCbor)

        val encryptionInfoCbor = encryptionInfoB64.base64UrlDecode()
        val encryptionInfo = coseCompliantCbor.decodeFromByteArray(DCAPIEncryptionInfo.serializer(), encryptionInfoCbor)


        // Verify that the key in encryptionInfo matches our recipientPrivateKey
        // (This step ensures we are the intended recipient defined in the info)
        val infoPublicKeyCose = encryptionInfo.encryptionParameters.recipientPublicKey
        val myPublicKeyCose = recipientPrivateKey.getCosePublicKey()

        check(infoPublicKeyCose == myPublicKeyCose) {
            "Recipient Private Key does not match the Public Key in EncryptionInfo"
        }

        val hpkeInfo = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin)

        val enc = encryptedResponse.response.enc
        val ciphertext = encryptedResponse.response.cipherText
        val combinedCiphertext = enc + ciphertext

        // 5. Decrypt using JWKKey
        return try {
            recipientPrivateKey.decryptHpke(
                cipherTextWithEnc = combinedCiphertext,
                info = hpkeInfo,
                aad = null
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("HPKE decryption failed (wrong key, origin/encryptionInfo mismatch, or corrupted response)", e)
        }
    }
}
