package id.walt.iso18013.annexc

import id.walt.cose.toCoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.hpke.Hpke
import id.walt.crypto2.keys.HpkeCiphertext
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonObject
import id.walt.crypto2.keys.Key as Crypto2Key

/**
 * The verifier must:
 * - parse CBOR EncryptedResponse = ["dcapi",{enc,cipherText}]
 * - compute SessionTranscript + HPKE info (bound to origin + encryptionInfoB64 string)
 * - HPKE-decrypt to DeviceResponse CBOR bytes
 */

object AnnexCResponseVerifier {
    suspend fun decryptToDeviceResponse(
        encryptedResponseB64: String,
        encryptionInfoB64: String,
        origin: String,
        recipientPrivateKey: Crypto2Key,
    ): ByteArray {
        val encryptedResponse = coseCompliantCbor.decodeFromByteArray(
            AnnexCEncryptedResponse.serializer(),
            encryptedResponseB64.base64UrlDecode(),
        )
        val encryptionInfo = coseCompliantCbor.decodeFromByteArray(
            DCAPIEncryptionInfo.serializer(),
            encryptionInfoB64.base64UrlDecode(),
        )
        val recipientPublicJwk = requireNotNull(recipientPrivateKey.capabilities.publicKeyExporter) {
            "Annex C recipient key does not export its public key"
        }.exportPublicKey().toPublicJwk(recipientPrivateKey.spec)
        check(encryptionInfo.encryptionParameters.recipientPublicKey == recipientPublicJwk.toCoseKey()) {
            "Recipient private key does not match the public key in EncryptionInfo"
        }
        val hpkeCiphertext = HpkeCiphertext(
            suite = Hpke.P256_HKDF_SHA256_AES_128_GCM,
            encapsulatedKey = BinaryData(encryptedResponse.response.enc),
            ciphertext = BinaryData(encryptedResponse.response.cipherText),
        )
        return try {
            Hpke.openBase(
                recipientKey = recipientPrivateKey,
                ciphertext = hpkeCiphertext,
                info = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin),
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw IllegalArgumentException(
                "HPKE decryption failed (wrong key, origin/encryptionInfo mismatch, or corrupted response)",
                cause,
            )
        }
    }

    @Deprecated("Use the crypto2 Key overload")
    suspend fun decryptToDeviceResponse(
        encryptedResponseB64: String,
        encryptionInfoB64: String,
        origin: String,
        recipientPrivateKey: JWKKey,
    ): ByteArray {
        val serialized = KeySerialization.serializeKeyToJson(recipientPrivateKey).jsonObject
        val migrated = V1KeyMigration().migrate(
            recordId = KeyId(recipientPrivateKey.getKeyId()),
            serialized = serialized,
            usages = setOf(KeyUsage.KEY_AGREEMENT),
        )
        val key = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).restore(migrated)
        return decryptToDeviceResponse(encryptedResponseB64, encryptionInfoB64, origin, key)
    }
}
