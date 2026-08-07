package id.walt.certificate.x509

import id.walt.crypto2.algorithms.DigestAlgorithm.Companion.SHA_256
import id.walt.crypto2.algorithms.DigestAlgorithm.Companion.SHA_384
import id.walt.crypto2.algorithms.DigestAlgorithm.Companion.SHA_512
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.algorithms.outputSizeBytes
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto.keys.Key as Crypto1Key
import id.walt.crypto.keys.KeyType as Crypto1KeyType

interface X509SigningAlgorithmInfo {
    val signingAlgorithmName: String
    val signingAlgorithmOid: String
    val keyAlgorithmName: String
    val keyAlgorithmOid: String
    val keyEllipticCurveOid: String?


    private data class Info(
        override val signingAlgorithmName: String,
        override val signingAlgorithmOid: String,
        override val keyAlgorithmName: String,
        override val keyAlgorithmOid: String,
        override val keyEllipticCurveOid: String?
    ) : X509SigningAlgorithmInfo


    companion object {

        const val KEY_ALG_NAME_EC = "id-ecPublicKey"
        const val KEY_ALG_OID_EC = "1.2.840.10045.2.1"

        const val KEY_ALG_NAME_RSA = "rsaEncryption"
        const val KEY_ALG_OID_RSA = "1.2.840.113549.1.1.1"

        fun ofKey(key: Key, signatureAlgorithm: SignatureAlgorithm): X509SigningAlgorithmInfo {
            return when (key.spec) {
                is KeySpec.Rsa -> {
                    when (signatureAlgorithm) {
                        is SignatureAlgorithm.RsaPkcs1 -> {
                            when (signatureAlgorithm.digest.name) {
                                SHA_256.name -> {
                                    Info(
                                        signingAlgorithmName = "sha256WithRSAEncryption",
                                        signingAlgorithmOid = "1.2.840.113549.1.1.11",
                                        keyAlgorithmName = KEY_ALG_NAME_RSA,
                                        keyAlgorithmOid = KEY_ALG_OID_RSA,
                                        keyEllipticCurveOid = null
                                    )
                                }

                                SHA_384.name -> {
                                    Info(
                                        signingAlgorithmName = "sha384WithRSAEncryption",
                                        signingAlgorithmOid = "1.2.840.113549.1.1.12",
                                        keyAlgorithmName = KEY_ALG_NAME_RSA,
                                        keyAlgorithmOid = KEY_ALG_OID_RSA,
                                        keyEllipticCurveOid = null
                                    )
                                }

                                SHA_512.name -> {
                                    Info(
                                        signingAlgorithmName = "sha512WithRSAEncryption",
                                        signingAlgorithmOid = "1.2.840.113549.1.1.13",
                                        keyAlgorithmName = KEY_ALG_NAME_RSA,
                                        keyAlgorithmOid = KEY_ALG_OID_RSA,
                                        keyEllipticCurveOid = null
                                    )
                                }

                                else -> throw IllegalArgumentException("Unsupported RSA digest: ${signatureAlgorithm.digest.name}")
                            }
                        }

                        else -> throw IllegalArgumentException("Unsupported RSA signature algorithm: ${signatureAlgorithm::class.simpleName} for key type: ${key.spec::class.simpleName}")
                    }
                }

                is KeySpec.Ec -> {
                    val ecKeySpec = key.spec as KeySpec.Ec
                    require(signatureAlgorithm is SignatureAlgorithm.Ecdsa) { "Expected ECDSA signature algorithm, got ${signatureAlgorithm::class.simpleName}" }
                    val ellipticCurve = when (ecKeySpec.curve) {
                        EcCurve.P256 -> "1.2.840.10045.3.1.7" //prime256v1 or secp256r1
                        EcCurve.P384 -> "1.3.132.0.34" // ansip384r1 or secp384r1
                        EcCurve.P521 -> "1.3.132.0.35" // ansip521r1 or secp521r1
                        EcCurve.SECP256K1 -> "1.3.132.0.10"
                        EcCurve.BRAINPOOL_P256R1 -> "1.3.36.3.3.2.8.1.1.7"
                        EcCurve.BRAINPOOL_P384R1 -> "1.3.36.3.3.2.8.1.1.11"
                        EcCurve.BRAINPOOL_P512R1 -> "1.3.36.3.3.2.8.1.1.13"
                        else -> throw IllegalArgumentException("Unsupported ECDSA curve: ${ecKeySpec.curve.name}")
                    }

                    when (signatureAlgorithm.digest.name) {
                        SHA_256.name -> {
                            Info(
                                signingAlgorithmName = "ecdsa-with-SHA256",
                                signingAlgorithmOid = "1.2.840.10045.4.3.2",
                                keyAlgorithmName = KEY_ALG_NAME_EC,
                                keyAlgorithmOid = KEY_ALG_OID_EC,
                                keyEllipticCurveOid = ellipticCurve
                            )
                        }

                        SHA_384.name -> {
                            Info(
                                signingAlgorithmName = "ecdsa-with-SHA384",
                                signingAlgorithmOid = "1.2.840.10045.4.3.3",
                                keyAlgorithmName = KEY_ALG_NAME_EC,
                                keyAlgorithmOid = KEY_ALG_OID_EC,
                                keyEllipticCurveOid = ellipticCurve
                            )
                        }

                        SHA_512.name -> {
                            Info(
                                signingAlgorithmName = "ecdsa-with-SHA512",
                                signingAlgorithmOid = "1.2.840.10045.4.3.4",
                                keyAlgorithmName = KEY_ALG_NAME_EC,
                                keyAlgorithmOid = KEY_ALG_OID_EC,
                                keyEllipticCurveOid = ellipticCurve
                            )
                        }

                        else -> {
                            throw IllegalArgumentException("Unsupported ECDSA digest: ${signatureAlgorithm.digest.name}")
                        }
                    }
                }

                is KeySpec.Edwards -> TODO()
                else -> throw IllegalArgumentException("Unsupported key type: ${key.spec::class.simpleName}")
            }
        }

        fun ofKey(key: Crypto1Key): X509SigningAlgorithmInfo =
            ofKeyType(key.keyType)

        fun ofKeyType(keyType: Crypto1KeyType): X509SigningAlgorithmInfo =
            signingAlgorithmInfoMap.get(keyType) ?: error("Unknown key type: $keyType")

        fun algorithmNameByOid(oid: String): String =
            oidToNameMap[oid] ?: oid


        private val signingAlgorithmInfoMap = mapOf(
            Crypto1KeyType.Ed25519 to Info(
                signingAlgorithmName = "id-Ed25519",
                signingAlgorithmOid = "1.3.101.112",
                keyAlgorithmName = "id-Ed25519",
                keyAlgorithmOid = "1.3.101.112",
                keyEllipticCurveOid = null
            ),

            Crypto1KeyType.secp256k1 to Info(
                signingAlgorithmName = "ecdsa-with-SHA256",
                signingAlgorithmOid = "1.2.840.10045.4.3.2",
                keyAlgorithmName = KEY_ALG_NAME_EC,
                keyAlgorithmOid = KEY_ALG_OID_EC,
                keyEllipticCurveOid = "1.3.132.0.10"
            ),

            Crypto1KeyType.secp256r1 to Info(
                signingAlgorithmName = "ecdsa-with-SHA256",
                signingAlgorithmOid = "1.2.840.10045.4.3.2",
                keyAlgorithmName = KEY_ALG_NAME_EC,
                keyAlgorithmOid = KEY_ALG_OID_EC,
                keyEllipticCurveOid = "1.2.840.10045.3.1.7"
            ),

            Crypto1KeyType.secp384r1 to Info(
                signingAlgorithmName = "ecdsa-with-SHA384",
                signingAlgorithmOid = "1.2.840.10045.4.3.3",
                keyAlgorithmName = KEY_ALG_NAME_EC,
                keyAlgorithmOid = KEY_ALG_OID_EC,
                keyEllipticCurveOid = "1.3.132.0.34"
            ),

            Crypto1KeyType.secp521r1 to Info(
                signingAlgorithmName = "ecdsa-with-SHA512",
                signingAlgorithmOid = "1.2.840.10045.4.3.4",
                keyAlgorithmName = KEY_ALG_NAME_EC,
                keyAlgorithmOid = KEY_ALG_OID_EC,
                keyEllipticCurveOid = "1.3.132.0.35"
            ),

            Crypto1KeyType.RSA to Info(
                signingAlgorithmName = "sha256WithRSAEncryption",
                signingAlgorithmOid = "1.2.840.113549.1.1.11",
                keyAlgorithmName = KEY_ALG_NAME_RSA,
                keyAlgorithmOid = "1.2.840.113549.1.1.1",
                keyEllipticCurveOid = null
            ),

            Crypto1KeyType.RSA3072 to Info(
                signingAlgorithmName = "sha384WithRSAEncryption",
                signingAlgorithmOid = "1.2.840.113549.1.1.12",
                keyAlgorithmName = KEY_ALG_NAME_RSA,
                keyAlgorithmOid = "1.2.840.113549.1.1.1",
                keyEllipticCurveOid = null
            ),
            Crypto1KeyType.RSA4096 to Info(
                signingAlgorithmName = "sha512WithRSAEncryption",
                signingAlgorithmOid = "1.2.840.113549.1.1.13",
                keyAlgorithmName = KEY_ALG_NAME_RSA,
                keyAlgorithmOid = "1.2.840.113549.1.1.1",
                keyEllipticCurveOid = null
            )
        )

        private val oidToNameMap = signingAlgorithmInfoMap.values
            .flatMap {
                listOf(
                    it.signingAlgorithmOid to it.signingAlgorithmName,
                    it.keyAlgorithmOid to it.keyAlgorithmName
                )
            }.toMap()


        fun SignatureAlgorithm.requireCompatibleWith(key: Key) {
            require(key.capabilities.supportsSignatureAlgorithm(this)) {
                "X.509 signing key does not support signature algorithm: $this"
            }
            when (this) {
                is SignatureAlgorithm.Ecdsa -> {
                    require(encoding == EcdsaSignatureEncoding.DER) {
                        "X.509 ECDSA signatures must use DER encoding"
                    }
                    val ec = key.spec as? KeySpec.Ec
                        ?: throw IllegalArgumentException("X.509 ECDSA requires an EC signing key")
                    require(ec.curve == EcCurve.P256 || ec.curve == EcCurve.P384 || ec.curve == EcCurve.P521) {
                        "Unsupported X.509 EC curve: ${ec.curve.name}"
                    }
                }

                is SignatureAlgorithm.RsaPkcs1 -> require(key.spec is KeySpec.Rsa) {
                    "X.509 RSA PKCS#1 signatures require an RSA signing key"
                }

                is SignatureAlgorithm.RsaPss -> {
                    require(key.spec is KeySpec.Rsa) {
                        "X.509 RSA-PSS signatures require an RSA signing key"
                    }
                    require(mgfDigest == digest) {
                        "X.509 RSA-PSS MGF digest must match the message digest"
                    }
                    require(saltLengthBytes == digest.outputSizeBytes) {
                        "X.509 RSA-PSS salt length must be explicit and match the digest length"
                    }
                }

                else -> throw IllegalArgumentException("Unsupported X.509 signature algorithm: $this")
            }
        }
    }
}
