package id.walt.openid4vci.responses.credential.encryption

import id.walt.crypto.utils.JweUtils
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object JweCredentialResponseEncryptor : CredentialResponseEncryptor {
    override fun encrypt(
        payload: JsonObject,
        encryption: CredentialResponseEncryptionParameters,
    ): String =
        JweUtils.toJWE(
            payload = payload,
            jwk = encryption.jwk.toString(),
            alg = CredentialEncryptionProfile.ALG_ECDH_ES,
            enc = CredentialEncryptionProfile.ENC_A128GCM,
            headerParams = buildMap<String, JsonElement> {
                put("kid", JsonPrimitive(requireNotNull(encryption.jwk["kid"]?.jsonPrimitive?.contentOrNull)))
            },
        )
}
