package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun interface CertificatePathValidator {
    fun validate(
        leaf: CertificateDer,
        chain: List<CertificateDer>,
        trustAnchors: List<CertificateDer>,
    )
}

class X509ChainClientAttestationVerifier private constructor(
    trustedRootCertificatesPem: List<String>,
    private val certificatePathValidator: CertificatePathValidator,
) : ClientAttestationVerifier {

    private val trustedRootCertificates = trustedRootCertificatesPem.map { CertificateDer.fromPEMEncodedString(it) }

    init {
        require(trustedRootCertificatesPem.isNotEmpty()) {
            "trustedRootCertificatesPem must not be empty"
        }
    }

    constructor(trustedRootCertificatesPem: List<String>) : this(
        trustedRootCertificatesPem,
        CertificatePathValidator { leaf, chain, trustAnchors ->
            validateCertificateChain(
                leaf = leaf,
                chain = chain,
                trustAnchors = trustAnchors,
                enableTrustedChainRoot = false,
                enableSystemTrustAnchors = false,
                enableRevocation = false,
            )
        },
    )

    internal companion object {
        fun withCertificatePathValidator(
            trustedRootCertificatesPem: List<String>,
            certificatePathValidator: CertificatePathValidator,
        ): X509ChainClientAttestationVerifier =
            X509ChainClientAttestationVerifier(trustedRootCertificatesPem, certificatePathValidator)
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun verifyAttestationJwt(
        jwt: String,
        header: JsonObject,
        payload: JsonObject,
    ): ClientAttestationVerificationResult {
        val certificateChain = header.x5cCertificates()
            ?: return ClientAttestationVerificationResult.Rejected("Client attestation x5c header is required")

        val leafCertificate = certificateChain.firstOrNull()
            ?: return ClientAttestationVerificationResult.Rejected("Client attestation x5c header is empty")

        val chainIsTrusted = runCatching {
            certificatePathValidator.validate(
                leafCertificate,
                certificateChain.drop(1),
                trustedRootCertificates,
            )
        }.isSuccess
        if (!chainIsTrusted) {
            return ClientAttestationVerificationResult.Rejected("Client attestation x5c chain is not trusted")
        }

        val leafKey = JWKKey.importFromDerCertificate(leafCertificate.bytes.toByteArray()).getOrNull()
        if (leafKey?.verifyJws(jwt)?.isSuccess != true) {
            return ClientAttestationVerificationResult.Rejected("Client attestation signature is invalid")
        }

        return ClientAttestationVerificationResult.Verified
    }

    private fun JsonObject.x5cCertificates(): List<CertificateDer>? {
        val x5c = this["x5c"] as? JsonArray ?: return null
        if (x5c.isEmpty()) return emptyList()

        return x5c.map { element ->
            val encodedCertificate = (element as? JsonPrimitive)?.contentOrNull ?: return null
            val der = runCatching { encodedCertificate.decodeFromBase64() }.getOrNull() ?: return null
            CertificateDer(der)
        }
    }
}
