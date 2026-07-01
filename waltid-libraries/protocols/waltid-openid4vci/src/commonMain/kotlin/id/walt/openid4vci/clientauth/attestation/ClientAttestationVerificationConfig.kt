package id.walt.openid4vci.clientauth.attestation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ClientAttestationVerificationConfig(
    val verificationMethod: ClientAttestationVerificationMethod,
)

@Serializable
sealed class ClientAttestationVerificationMethod {
    @Serializable
    @SerialName("static-jwk")
    data class StaticJwk(
        val jwk: JsonObject,
    ) : ClientAttestationVerificationMethod()

    @Serializable
    @SerialName("x509-chain")
    data class X509Chain(
        val trustedRootCertificatesPem: List<String>,
    ) : ClientAttestationVerificationMethod() {
        init {
            require(trustedRootCertificatesPem.isNotEmpty()) {
                "x509-chain verification requires at least one trusted root certificate"
            }
        }
    }

    @Serializable
    @SerialName("key-reference")
    data class KeyReference(
        val reference: String,
    ) : ClientAttestationVerificationMethod() {
        init {
            require(reference.isNotBlank()) { "key-reference verification requires a non-blank reference" }
        }
    }
}