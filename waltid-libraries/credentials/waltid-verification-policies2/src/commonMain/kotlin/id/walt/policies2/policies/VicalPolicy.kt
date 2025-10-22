package id.walt.policies2.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.vical.Vical
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
@SerialName("vical")
data class VicalPolicy(
    /** the trusted VICAL file (base64 or hex encoded) */
    val vical: String,
    val enableRevocation: Boolean = false
) : VerificationPolicy2() {
    override val id = "vical"

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {

        try {

            val credentialSignature = credential.signature
            if (!(credential is MdocsCredential && credentialSignature is CoseCredentialSignature))
                throw IllegalArgumentException("VICAL policy can currently only be applied to mdocs")

            val x5cList =
                credentialSignature.x5cList ?: throw IllegalArgumentException("Credential has x5c no x5c list")

            // Loading document signing certificate from chain
            // TODO: check if this is the right key that signed the credential
            val signingCert = CertificateDer(
                x5cList.x5c.firstOrNull()?.base64Der?.decodeFromBase64()
                    ?: throw IllegalArgumentException("Could not determine document signer credential in x5c list")
            )

            // Loading the certificate chain from the provided credential
            val chain = x5cList.x5c.map {
                CertificateDer(it.base64Der.decodeFromBase64())
            }.filter { it != signingCert } // Do not put the signing cert in the chain

            // Decoding trust anchors from trusted VICAL
            val vical = Vical.decode(vical.decodeFromBase64())
            // TODO load anchors from vical.vicalData...
            val anchors = chain //fix

            validateCertificateChain(signingCert, chain, anchors, enableRevocation)


        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(
            JsonPrimitive(true)
        )
    }
}
