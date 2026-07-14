package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.clientauth.attestation.ClientAttestationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ClientAttestationVerifierConfig(
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

suspend fun ClientAttestationVerifierConfig.toClientAttestationConfig(
    keyReferenceResolver: ClientAttestationKeyReferenceResolver? = null,
): ClientAttestationConfig =
    when (val source = verificationMethod) {
        is ClientAttestationVerificationMethod.StaticJwk -> {
            val trustedKey = JWKKey.importJWK(source.jwk.toString()).getOrThrow()
            ClientAttestationConfig(
                attestationVerifier = KeyBasedClientAttestationVerifier { _, _ -> listOf(trustedKey) },
            )
        }

        is ClientAttestationVerificationMethod.X509Chain ->
            ClientAttestationConfig(
                attestationVerifier = X509ChainClientAttestationVerifier(source.trustedRootCertificatesPem),
            )

        is ClientAttestationVerificationMethod.KeyReference -> {
            val resolver = requireNotNull(keyReferenceResolver) {
                "key-reference client attestation verification requires a key reference resolver"
            }
            ClientAttestationConfig(
                attestationVerifier = KeyBasedClientAttestationVerifier { header, payload ->
                    resolver.resolveTrustedAttesterKeys(source.reference, header, payload)
                },
            )
        }
    }
