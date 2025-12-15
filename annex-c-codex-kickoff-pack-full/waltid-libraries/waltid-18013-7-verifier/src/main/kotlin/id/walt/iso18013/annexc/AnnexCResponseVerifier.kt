package id.walt.iso18013.annexc

/**
 * Implementations must:
 * - parse CBOR EncryptedResponse = ["dcapi",{enc,cipherText}]
 * - compute SessionTranscript + HPKE info (bound to origin + encryptionInfoB64 string)
 * - HPKE-decrypt to DeviceResponse CBOR bytes
 */
interface AnnexCResponseVerifier {
    fun decryptToDeviceResponse(
        encryptedResponseB64: String,
        encryptionInfoB64: String,
        origin: String,
        recipientPrivateKey: ByteArray
    ): ByteArray
}
