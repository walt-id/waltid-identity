package id.walt.vical

import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.Base64Utils.decodeFromBase64

object VicalService {
    suspend fun validateVical(vicalValidationRequest: VicalValidationRequest): VicalValidationResponse {
        val verificationKey = KeyManager.resolveSerializedKey(vicalValidationRequest.verificationKey)

        val vical = Vical.decode(vicalValidationRequest.vicalBase64.decodeFromBase64())

        val isVicalValid = vical.verify(verificationKey.toCoseVerifier())
        return VicalValidationResponse(isVicalValid)
    }
}