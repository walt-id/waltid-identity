package id.walt.certificate.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType

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
        const val KEY_ALG_NAME_RSA = "rsaEncryption"

        fun ofKey(key: Key): X509SigningAlgorithmInfo =
            signingAlgorithmInfoMap.get(key.keyType) ?: error("Unknown key type: $key.keyType")

        fun algorithmNameByOid(oid: String): String =
            oidToNameMap[oid] ?: oid


        private val signingAlgorithmInfoMap = mapOf(
            KeyType.Ed25519 to Info(
                signingAlgorithmName = "id-Ed25519",
                signingAlgorithmOid = "1.3.101.112",
                keyAlgorithmName = "id-Ed25519",
                keyAlgorithmOid = "1.3.101.112",
                keyEllipticCurveOid = null
            ),

            KeyType.secp256k1 to Info(
                signingAlgorithmName = "ecdsa-with-SHA256",
                signingAlgorithmOid = "1.2.840.10045.4.3.2",
                keyAlgorithmName = KEY_ALG_NAME_EC,
                keyAlgorithmOid = "1.2.840.10045.2.1",
                keyEllipticCurveOid = "1.3.132.0.10"
            ),

            KeyType.secp256r1 to Info(
                signingAlgorithmName = "ecdsa-with-SHA256",
                signingAlgorithmOid = "1.2.840.10045.4.3.2",
                keyAlgorithmName = KEY_ALG_NAME_EC,
                keyAlgorithmOid = "1.2.840.10045.2.1",
                keyEllipticCurveOid = "1.2.840.10045.3.1.7"
            ),

            KeyType.secp384r1 to Info(
                signingAlgorithmName = "ecdsa-with-SHA384",
                signingAlgorithmOid = "1.2.840.10045.4.3.3",
                keyAlgorithmName = KEY_ALG_NAME_EC,
                keyAlgorithmOid = "1.2.840.10045.2.1",
                keyEllipticCurveOid = "1.3.132.0.34"
            ),

            KeyType.secp521r1 to Info(
                signingAlgorithmName = "ecdsa-with-SHA512",
                signingAlgorithmOid = "1.2.840.10045.4.3.4",
                keyAlgorithmName = KEY_ALG_NAME_EC,
                keyAlgorithmOid = "1.2.840.10045.2.1",
                keyEllipticCurveOid = "1.3.132.0.35"
            ),

            KeyType.RSA to Info(
                signingAlgorithmName = "sha256WithRSAEncryption",
                signingAlgorithmOid = "1.2.840.113549.1.1.11",
                keyAlgorithmName = KEY_ALG_NAME_RSA,
                keyAlgorithmOid = "1.2.840.113549.1.1.1",
                keyEllipticCurveOid = null
            ),

            KeyType.RSA3072 to Info(
                signingAlgorithmName = "sha384WithRSAEncryption",
                signingAlgorithmOid = "1.2.840.113549.1.1.12",
                keyAlgorithmName = KEY_ALG_NAME_RSA,
                keyAlgorithmOid = "1.2.840.113549.1.1.1",
                keyEllipticCurveOid = null
            ),
            KeyType.RSA4096 to Info(
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
    }
}
