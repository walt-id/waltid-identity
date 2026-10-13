package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.*
import id.walt.certificate.x509.X509SigningAlgorithmInfo

object SignumSignatureAlgorithmUtil {

    fun X509SigningAlgorithmInfo.toSignatureAlgorithm(): SignatureAlgorithm =
        when (signingAlgorithmOid) {
            "1.2.840.10045.4.3.2" -> {
                SignatureAlgorithm.ECDSA(Digest.SHA256, ECCurve.SECP_256_R_1)
            }

            "1.2.840.10045.4.3.3" -> {
                SignatureAlgorithm.ECDSA(Digest.SHA384, ECCurve.SECP_384_R_1)
            }

            "1.2.840.10045.4.3.4" -> {
                SignatureAlgorithm.ECDSA(Digest.SHA512, ECCurve.SECP_521_R_1)
            }

            "1.2.840.113549.1.1.11" -> {
                SignatureAlgorithm.RSA(Digest.SHA256, RSAPadding.PKCS1)
            }

            "1.2.840.113549.1.1.12" -> {
                SignatureAlgorithm.RSA(Digest.SHA384, RSAPadding.PKCS1)
            }

            "1.2.840.113549.1.1.13" -> {
                SignatureAlgorithm.RSA(Digest.SHA512, RSAPadding.PKCS1)
            }

            else -> {
                throw IllegalArgumentException("Unsupported Hash Alogorithm '${signingAlgorithmName}' (OID: '${signingAlgorithmOid}')")
            }
        }

    /**
     * Legacy keys return either DER or raw EC components. Preserve their existing DER preference,
     * but select the signature algorithm before decoding: raw bytes can resemble unrelated ASN.1.
     */
    fun evaluateSignature(algorithm: X509SigningAlgorithmInfo, signatureRaw: ByteArray): CryptoSignature =
        when (algorithm.toSignatureAlgorithm()) {
            is SignatureAlgorithm.ECDSA -> CryptoSignature.EC.decodeFromDerOrNull(signatureRaw)
                ?: CryptoSignature.EC.fromRawBytes(signatureRaw)
            is SignatureAlgorithm.RSA -> CryptoSignature.RSA(signatureRaw)
        }

    /** Crypto2 X.509 callers explicitly request DER for ECDSA; RSA signatures remain opaque bytes. */
    internal fun evaluateDerSignature(algorithm: X509SigningAlgorithmInfo, signatureRaw: ByteArray): CryptoSignature =
        when (algorithm.toSignatureAlgorithm()) {
            is SignatureAlgorithm.ECDSA -> CryptoSignature.EC.decodeFromDer(signatureRaw)
            is SignatureAlgorithm.RSA -> CryptoSignature.RSA(signatureRaw)
        }
}
