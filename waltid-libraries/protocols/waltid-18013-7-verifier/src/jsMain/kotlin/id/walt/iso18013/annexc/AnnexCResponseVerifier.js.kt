package id.walt.iso18013.annexc

actual object AnnexCResponseVerifier {
    actual suspend fun decryptToDeviceResponse(
        encryptedResponseB64: String,
        encryptionInfoB64: String,
        origin: String,
        recipientPrivateKey: id.walt.crypto.keys.jwk.JWKKey
    ): ByteArray {
        TODO("Not yet implemented")
    }
}
