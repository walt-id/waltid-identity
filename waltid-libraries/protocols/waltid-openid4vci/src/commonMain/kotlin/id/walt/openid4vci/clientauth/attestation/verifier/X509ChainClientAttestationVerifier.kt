package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class X509ChainClientAttestationVerifier(
    trustedRootCertificatesPem: List<String>,
) : ClientAttestationVerifier {

    private val trustedRootCertificates = trustedRootCertificatesPem.map { CertificateDer.fromPEMEncodedString(it) }
    private val keyResolver = Crypto2JwtKeyResolver()

    init {
        require(trustedRootCertificatesPem.isNotEmpty()) {
            "trustedRootCertificatesPem must not be empty"
        }
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
            validateCertificateChain(
                leaf = leafCertificate,
                chain = certificateChain.drop(1),
                trustAnchors = trustedRootCertificates,
                enableTrustedChainRoot = false,
                enableSystemTrustAnchors = false,
                enableRevocation = false,
            )
        }.isSuccess
        if (!chainIsTrusted) {
            return ClientAttestationVerificationResult.Rejected("Client attestation x5c chain is not trusted")
        }
        val leafKey = keyResolver.resolveFromJwt(header, JsonObject(emptyMap()))?.key
            ?: return ClientAttestationVerificationResult.Rejected("Client attestation certificate key is invalid")
        val algorithm = runCatching {
            JwsAlgorithm.parse(requireNotNull(header["alg"]?.jsonPrimitive?.contentOrNull))
        }.getOrElse {
            return ClientAttestationVerificationResult.Rejected("Client attestation algorithm is invalid")
        }
        try {
            CompactJws.verify(jwt, leafKey, algorithm)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
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
