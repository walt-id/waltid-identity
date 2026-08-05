@file:Suppress("DEPRECATION")

package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.openid4vci.clientauth.attestation.ClientAttestationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    crypto2KeyReferenceResolver: Crypto2ClientAttestationKeyReferenceResolver? = null,
): ClientAttestationConfig =
    when (val source = verificationMethod) {
        is ClientAttestationVerificationMethod.StaticJwk -> {
            val trustedKey = requireNotNull(
                Crypto2JwtKeyResolver(allowInlineJwk = true).resolveFromJwt(
                    jwtHeader = buildJsonObject { put("jwk", source.jwk) },
                    jwtPayload = JsonObject(emptyMap()),
                )?.key
            ) { "static-jwk client attestation verification key is invalid" }
            ClientAttestationConfig(
                attestationVerifier = Crypto2KeyBasedClientAttestationVerifier { _, _ -> listOf(trustedKey) },
            )
        }

        is ClientAttestationVerificationMethod.X509Chain ->
            ClientAttestationConfig(
                attestationVerifier = X509ChainClientAttestationVerifier(source.trustedRootCertificatesPem),
            )

        is ClientAttestationVerificationMethod.KeyReference -> {
            ClientAttestationConfig(
                attestationVerifier = ReferencedClientAttestationVerifier(
                    reference = source.reference,
                    crypto2Resolver = crypto2KeyReferenceResolver,
                    legacyResolver = keyReferenceResolver,
                ),
            )
        }
    }

@Deprecated("Retained for binary compatibility", level = DeprecationLevel.HIDDEN)
suspend fun ClientAttestationVerifierConfig.toClientAttestationConfig(
    keyReferenceResolver: ClientAttestationKeyReferenceResolver? = null,
): ClientAttestationConfig = toClientAttestationConfig(
    keyReferenceResolver = keyReferenceResolver,
    crypto2KeyReferenceResolver = null,
)
