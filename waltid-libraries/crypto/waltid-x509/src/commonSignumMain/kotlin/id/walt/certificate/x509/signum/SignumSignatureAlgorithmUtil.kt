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
     * Fix strange behavior of id.walt.crypto.keys.Key.signRaw
     * JVM implementation returns DER encoded signature, while JS implementation returns raw bytes
     */
    fun evaluateSignature(algorithm: X509SigningAlgorithmInfo, signatureRaw: ByteArray): CryptoSignature =
        runCatching {
            CryptoSignature.decodeFromDer(signatureRaw)
        }.getOrElse {
            //JS implementation of Key doesn't provide DER encoded signature, so we need to build it our selfe
            when (algorithm.signingAlgorithmOid) {
                "1.2.840.10045.4.3.2",
                "1.2.840.10045.4.3.3",
                "1.2.840.10045.4.3.4" -> {
                    CryptoSignature.EC.fromRawBytes(signatureRaw)
                }

                "1.2.840.113549.1.1.11",
                "1.2.840.113549.1.1.12",
                "1.2.840.113549.1.1.13" -> {
                    CryptoSignature.RSA(signatureRaw)
                }

                else -> {
                    throw IllegalArgumentException("Unsupported Hash Algorithm '${algorithm.signingAlgorithmName}' (OID: '${algorithm.signingAlgorithmOid}')")
                }
            }
        }
}