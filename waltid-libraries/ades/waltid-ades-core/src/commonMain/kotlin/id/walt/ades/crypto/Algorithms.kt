package id.walt.ades.crypto

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.DerWriter
import id.walt.ades.asn1.Oids
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeySpec

/** Parsed SubjectPublicKeyInfo. */
class PublicKeyInfo(val encoded: ByteArray) {
    private val element = Der.parse(encoded).expectSequence()
    val algorithmOid: String = element.child(0).child(0).asOid()
    val algorithmParameters: DerElement? = element.child(0).children.getOrNull(1)
    val keyBits: ByteArray = element.child(1).asBitStringBytes()

    val keySpec: KeySpec = when (algorithmOid) {
        Oids.EC_PUBLIC_KEY -> KeySpec.Ec(
            when (val curve = algorithmParameters?.asOid()) {
                Oids.CURVE_P256 -> EcCurve.P256
                Oids.CURVE_P384 -> EcCurve.P384
                Oids.CURVE_P521 -> EcCurve.P521
                Oids.CURVE_SECP256K1 -> EcCurve.SECP256K1
                Oids.CURVE_BRAINPOOL_P256R1 -> EcCurve.BRAINPOOL_P256R1
                Oids.CURVE_BRAINPOOL_P384R1 -> EcCurve.BRAINPOOL_P384R1
                Oids.CURVE_BRAINPOOL_P512R1 -> EcCurve.BRAINPOOL_P512R1
                else -> throw IllegalArgumentException("Unsupported EC curve $curve")
            }
        )

        Oids.RSA_ENCRYPTION, Oids.RSASSA_PSS -> {
            val modulus = Der.parse(keyBits).child(0).asIntegerBytes()
            val magnitude = modulus.dropWhile { it == 0.toByte() }
            val bits = if (magnitude.isEmpty()) 0 else (magnitude.size - 1) * 8 + (32 - (magnitude[0].toInt() and 0xff).countLeadingZeroBits())
            KeySpec.Rsa(bits)
        }

        Oids.ED25519 -> KeySpec.Edwards(EdwardsCurve.ED25519)
        Oids.ED448 -> KeySpec.Edwards(EdwardsCurve.ED448)
        else -> throw IllegalArgumentException("Unsupported public key algorithm $algorithmOid")
    }

    /** Size of one ECDSA signature component (r or s) in bytes, null for non-EC keys. */
    val ecComponentSize: Int?
        get() = when ((keySpec as? KeySpec.Ec)?.curve) {
            EcCurve.P256, EcCurve.SECP256K1, EcCurve.BRAINPOOL_P256R1 -> 32
            EcCurve.P384, EcCurve.BRAINPOOL_P384R1 -> 48
            EcCurve.P521 -> 66
            EcCurve.BRAINPOOL_P512R1 -> 64
            else -> null
        }

    val keySizeBits: Int
        get() = when (val s = keySpec) {
            is KeySpec.Rsa -> s.bits
            is KeySpec.Ec -> (ecComponentSize ?: 0) * 8
            is KeySpec.Edwards -> if (s.curve == EdwardsCurve.ED25519) 256 else 456
            else -> 0
        }
}

enum class SignatureScheme { ECDSA, RSA_PKCS1, RSA_PSS, EDDSA }

/**
 * A signature algorithm with its identifiers across AdES serializations. ECDSA signatures are P1363 (r||s) in
 * COSE, JWS and XMLDSig and DER in CMS/X.509; [crypto2] selects the encoding per use.
 */
enum class AdesSignatureAlgorithm(
    val scheme: SignatureScheme,
    val digest: AdesDigestAlgorithm?,
    val x509Oid: String?,
    val coseId: Int?,
    val jwsName: String?,
    val xmlUri: String?,
) {
    ES256(SignatureScheme.ECDSA, AdesDigestAlgorithm.SHA256, Oids.ECDSA_SHA256, -7, "ES256", "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"),
    ES384(SignatureScheme.ECDSA, AdesDigestAlgorithm.SHA384, Oids.ECDSA_SHA384, -35, "ES384", "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384"),
    ES512(SignatureScheme.ECDSA, AdesDigestAlgorithm.SHA512, Oids.ECDSA_SHA512, -36, "ES512", "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"),
    ES224(SignatureScheme.ECDSA, AdesDigestAlgorithm.SHA224, Oids.ECDSA_SHA224, null, null, "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha224"),
    ES1(SignatureScheme.ECDSA, AdesDigestAlgorithm.SHA1, Oids.ECDSA_SHA1, null, null, "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1"),
    RS256(SignatureScheme.RSA_PKCS1, AdesDigestAlgorithm.SHA256, Oids.SHA256_RSA, -257, "RS256", "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"),
    RS384(SignatureScheme.RSA_PKCS1, AdesDigestAlgorithm.SHA384, Oids.SHA384_RSA, -258, "RS384", "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384"),
    RS512(SignatureScheme.RSA_PKCS1, AdesDigestAlgorithm.SHA512, Oids.SHA512_RSA, -259, "RS512", "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"),
    RS224(SignatureScheme.RSA_PKCS1, AdesDigestAlgorithm.SHA224, Oids.SHA224_RSA, null, null, "http://www.w3.org/2001/04/xmldsig-more#rsa-sha224"),
    RS1(SignatureScheme.RSA_PKCS1, AdesDigestAlgorithm.SHA1, Oids.SHA1_RSA, -65535, null, "http://www.w3.org/2000/09/xmldsig#rsa-sha1"),
    PS256(SignatureScheme.RSA_PSS, AdesDigestAlgorithm.SHA256, Oids.RSASSA_PSS, -37, "PS256", "http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1"),
    PS384(SignatureScheme.RSA_PSS, AdesDigestAlgorithm.SHA384, Oids.RSASSA_PSS, -38, "PS384", "http://www.w3.org/2007/05/xmldsig-more#sha384-rsa-MGF1"),
    PS512(SignatureScheme.RSA_PSS, AdesDigestAlgorithm.SHA512, Oids.RSASSA_PSS, -39, "PS512", "http://www.w3.org/2007/05/xmldsig-more#sha512-rsa-MGF1"),
    ED25519(SignatureScheme.EDDSA, null, Oids.ED25519, -8, "EdDSA", "http://www.w3.org/2021/04/xmldsig-more#eddsa-ed25519"),
    ED448(SignatureScheme.EDDSA, null, Oids.ED448, null, null, "http://www.w3.org/2021/04/xmldsig-more#eddsa-ed448"),
    ;

    /** crypto2 algorithm descriptor. [derEcdsa] = true for CMS/X.509/OCSP/CRL signatures. */
    fun crypto2(derEcdsa: Boolean = false): SignatureAlgorithm = when (scheme) {
        SignatureScheme.ECDSA -> SignatureAlgorithm.Ecdsa(
            digest!!.toCrypto2(),
            if (derEcdsa) EcdsaSignatureEncoding.DER else EcdsaSignatureEncoding.IEEE_P1363,
        )

        SignatureScheme.RSA_PKCS1 -> SignatureAlgorithm.RsaPkcs1(digest!!.toCrypto2())
        SignatureScheme.RSA_PSS -> SignatureAlgorithm.RsaPss(digest!!.toCrypto2(), saltLengthBytes = digest.outputSize)
        SignatureScheme.EDDSA -> SignatureAlgorithm.EdDsa
    }

    /** DER AlgorithmIdentifier for use in CMS SignerInfo / X.509. */
    fun algorithmIdentifier(): ByteArray = when (scheme) {
        SignatureScheme.RSA_PSS -> DerWriter.algorithmIdentifier(
            Oids.RSASSA_PSS,
            DerWriter.sequence(
                DerWriter.explicit(0, digest!!.algorithmIdentifier()),
                DerWriter.explicit(1, DerWriter.algorithmIdentifier(Oids.MGF1, digest.algorithmIdentifier())),
                DerWriter.explicit(2, DerWriter.integer(digest.outputSize.toLong())),
            ),
        )

        SignatureScheme.RSA_PKCS1 -> DerWriter.algorithmIdentifier(x509Oid!!, DerWriter.nullValue())
        else -> DerWriter.algorithmIdentifier(x509Oid!!)
    }

    companion object {
        fun fromCoseId(id: Int): AdesSignatureAlgorithm = when (id) {
            -9 -> ES256 // ESP256 (fully specified ES256 on P-256)
            -51 -> ES384
            -52 -> ES512
            -19 -> ED25519
            -53 -> ED448
            else -> entries.firstOrNull { it.coseId == id } ?: throw IllegalArgumentException("Unsupported COSE algorithm $id")
        }

        fun fromJwsName(name: String): AdesSignatureAlgorithm =
            entries.firstOrNull { it.jwsName == name } ?: throw IllegalArgumentException("Unsupported JWS algorithm $name")

        fun fromXmlUri(uri: String): AdesSignatureAlgorithm =
            entries.firstOrNull { it.xmlUri == uri } ?: throw IllegalArgumentException("Unsupported XML signature algorithm $uri")

        /** Resolves an X.509/CMS AlgorithmIdentifier (sequence element). [publicKey] disambiguates CMS ecPublicKey/rsaEncryption. */
        fun fromAlgorithmIdentifier(algorithmIdentifier: DerElement, digestHint: AdesDigestAlgorithm? = null): AdesSignatureAlgorithm {
            val oid = algorithmIdentifier.child(0).asOid()
            return when (oid) {
                Oids.RSASSA_PSS -> {
                    val params = algorithmIdentifier.children.getOrNull(1)
                    val hashOid = params?.contextChild(0)?.explicitInner()?.child(0)?.asOid() ?: Oids.SHA1
                    when (AdesDigestAlgorithm.fromOid(hashOid)) {
                        AdesDigestAlgorithm.SHA256 -> PS256
                        AdesDigestAlgorithm.SHA384 -> PS384
                        AdesDigestAlgorithm.SHA512 -> PS512
                        else -> throw IllegalArgumentException("Unsupported RSASSA-PSS digest $hashOid")
                    }
                }
                // Some CMS producers put the key algorithm in signatureAlgorithm; combine with the digest algorithm.
                Oids.RSA_ENCRYPTION -> when (digestHint) {
                    AdesDigestAlgorithm.SHA256 -> RS256
                    AdesDigestAlgorithm.SHA384 -> RS384
                    AdesDigestAlgorithm.SHA512 -> RS512
                    AdesDigestAlgorithm.SHA1 -> RS1
                    AdesDigestAlgorithm.SHA224 -> RS224
                    else -> throw IllegalArgumentException("rsaEncryption without usable digest hint")
                }

                Oids.EC_PUBLIC_KEY -> when (digestHint) {
                    AdesDigestAlgorithm.SHA256 -> ES256
                    AdesDigestAlgorithm.SHA384 -> ES384
                    AdesDigestAlgorithm.SHA512 -> ES512
                    else -> throw IllegalArgumentException("ecPublicKey without usable digest hint")
                }

                else -> entries.firstOrNull { it.x509Oid == oid && it.scheme != SignatureScheme.RSA_PSS }
                    ?: throw IllegalArgumentException("Unsupported signature algorithm OID $oid")
            }
        }

        /** Default algorithm for a key: ECDSA with the curve-matching digest, RSA-PSS-free PKCS#1 v1.5 SHA-256, EdDSA. */
        fun defaultFor(keySpec: KeySpec): AdesSignatureAlgorithm = when (keySpec) {
            KeySpec.Ec(EcCurve.P256) -> ES256
            KeySpec.Ec(EcCurve.P384) -> ES384
            KeySpec.Ec(EcCurve.P521) -> ES512
            is KeySpec.Ec -> ES256
            is KeySpec.Rsa -> RS256
            KeySpec.Edwards(EdwardsCurve.ED25519) -> ED25519
            KeySpec.Edwards(EdwardsCurve.ED448) -> ED448
            else -> throw IllegalArgumentException("No default signature algorithm for $keySpec")
        }
    }
}
