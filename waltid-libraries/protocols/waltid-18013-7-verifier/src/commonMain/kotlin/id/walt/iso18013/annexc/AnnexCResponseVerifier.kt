package id.walt.iso18013.annexc

import id.walt.crypto.keys.jwk.JWKKey

/**
 * Implementations must:
 * - parse CBOR EncryptedResponse = ["dcapi",{enc,cipherText}]
 * - compute SessionTranscript + HPKE info (bound to origin + encryptionInfoB64 string)
 * - HPKE-decrypt to DeviceResponse CBOR bytes
 */

@Suppress("KotlinNoActualForExpect") // HPKE JS
expect object AnnexCResponseVerifier {
    suspend fun decryptToDeviceResponse(
        encryptedResponseB64: String,
        encryptionInfoB64: String,
        origin: String,
        recipientPrivateKey: JWKKey
    ): ByteArray
}

