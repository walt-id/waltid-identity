package id.walt.vical

import id.walt.cose.protectedAlgorithm
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

object VicalService {
    suspend fun validateVical(vicalValidationRequest: VicalValidationRequest): VicalValidationResponse {
        require(!Jwk.containsPrivateMaterial(vicalValidationRequest.verificationKey)) {
            "VICAL verification JWK must not contain private key material"
        }
        val encodedJwk = EncodedKey.Jwk(
            data = BinaryData(Json.encodeToString(vicalValidationRequest.verificationKey).encodeToByteArray()),
            privateMaterial = false,
        )
        val stored = encodedJwk.toStoredSoftwareKey(
            id = KeyId(Jwk.sha256Thumbprint(encodedJwk)),
            usages = setOf(KeyUsage.VERIFY),
        )
        val verificationKey = crypto2Runtime.restore(stored)
        val vical = Vical.decode(Base64.Default.decode(vicalValidationRequest.vicalBase64))
        return VicalValidationResponse(vical.verify(verificationKey, setOf(vical.coseSign1.protectedAlgorithm())))
    }

    suspend fun validateVical(
        vicalBase64: String,
        verificationKey: Key,
        allowedAlgorithms: Set<Int>,
    ): VicalValidationResponse {
        val vical = Vical.decode(Base64.Default.decode(vicalBase64))
        return VicalValidationResponse(vical.verify(verificationKey, allowedAlgorithms))
    }

    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
}
