package id.walt.openid4vci.metadata.issuer.signing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One signing strategy, initialized by the embedding service. Omit this config to reuse its token key. */
@Serializable
data class SignedMetadataConfig(val signingMethod: MetadataSigningMethod)

@Serializable
sealed interface MetadataSigningMethod {
    @Serializable
    @SerialName("static-jwk")
    data class StaticJwk(val jwk: JsonObject) : MetadataSigningMethod {
        init {
            require(jwk.isNotEmpty()) { "signedMetadata.jwk must not be empty" }
        }

        override fun toString(): String = "MetadataSigningMethod.StaticJwk([redacted])"
    }

    @Serializable
    @SerialName("x509-chain")
    data class X509Chain(
        val privateKeyPem: String,
        val certificateChainPem: List<String>,
    ) : MetadataSigningMethod {
        init {
            require(privateKeyPem.isNotBlank()) { "signedMetadata.privateKeyPem must not be blank" }
            require(certificateChainPem.isNotEmpty() && certificateChainPem.all { it.isNotBlank() }) {
                "signedMetadata.certificateChainPem must contain PEM certificates, leaf first"
            }
        }

        override fun toString(): String = "MetadataSigningMethod.X509Chain([redacted])"
    }

    @Serializable
    @SerialName("key-reference")
    data class KeyReference(val reference: String) : MetadataSigningMethod {
        init {
            require(reference.isNotBlank()) { "Metadata signing key reference must not be blank" }
        }

        override fun toString(): String = "MetadataSigningMethod.KeyReference([redacted])"
    }
}
