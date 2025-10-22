package id.walt.policies2.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SerialName("vical")
data class VicalPolicy(
    /** the VICAL file (base64 or hex encoded) */
    val vical: String
) : VerificationPolicy2() {
    override val id = "vical"

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        val credentialSignature = credential.signature
        if (credential is MdocsCredential && credentialSignature is CoseCredentialSignature) {
            val x5cList = credentialSignature.x5cList
            requireNotNull(x5cList)

            x5cList.x5c.forEach {
                it.base64Der // Parse VICAL here
            }

            // TODO("Not yet implemented")

            val isRootTrusted = true

            return if (isRootTrusted) {
                Result.success(
                    mapOf(
                        "" to ""
                    ).toJsonElement()
                )
            } else {
                Result.failure(
                    IllegalArgumentException("...")
                )
            }
        } else {
            throw NotImplementedError("VICAL policy can currently only be applied to mdocs")
        }
    }
}
