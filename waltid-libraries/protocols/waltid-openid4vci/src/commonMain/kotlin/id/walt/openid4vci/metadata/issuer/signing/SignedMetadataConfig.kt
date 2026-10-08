package id.walt.openid4vci.metadata.issuer.signing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmOverloads

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
    data class KeyReference @JvmOverloads constructor(
        val reference: String,
        /** Optional certificate-store references, leaf first. Overrides certificates supplied by the key resolver. */
        val x5cReferences: List<String>? = null,
    ) : MetadataSigningMethod {
        init {
            require(reference.isNotBlank()) { "Metadata signing key reference must not be blank" }
            require(x5cReferences == null ||
                x5cReferences.isNotEmpty() && x5cReferences.all { it.isNotBlank() }) {
                "signedMetadata.x5cReferences must contain certificate references, leaf first"
            }
        }

        override fun toString(): String = "MetadataSigningMethod.KeyReference([redacted])"
    }
}
