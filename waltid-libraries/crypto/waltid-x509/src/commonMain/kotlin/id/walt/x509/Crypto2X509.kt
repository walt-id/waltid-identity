package id.walt.x509

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.SignatureAlgorithm as SignumSignatureAlgorithm
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import id.walt.crypto.keys.Key as LegacyKey
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.x509.iso.authorityKeyIdentifierExtension
import id.walt.x509.iso.basicConstraintsExtension
import id.walt.x509.iso.buildSignumX509CertificateDer
import id.walt.x509.iso.crlDistributionPointExtension
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.extendedKeyUsageExtension
import id.walt.x509.iso.generateIsoCompliantX509CertificateSerialNo
import id.walt.x509.iso.issuerAlternativeNameExtension
import id.walt.x509.iso.keyUsageExtension
import id.walt.x509.iso.subjectAlternativeNamesExtension
import id.walt.x509.iso.subjectKeyIdentifierExtension
import id.walt.x509.iso.toSignumName
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

fun CertificateDer.crypto2PublicJwk(): EncodedKey.Jwk {
    val publicKey = X509Certificate.decodeFromDer(bytes.toByteArray()).decodedPublicKey.getOrThrow()
    return publicKey.toCrypto2PublicJwk()
}

fun CertificateSigningRequestDer.crypto2PublicJwk(): EncodedKey.Jwk =
    Pkcs10CertificationRequest.decodeFromDer(bytes.toByteArray()).tbsCsr.publicKey.toCrypto2PublicJwk()

internal fun CryptoPublicKey.toCrypto2PublicJwk(): EncodedKey.Jwk =
    EncodedKey.Jwk(
        data = BinaryData(
            joseCompliantSerializer.encodeToString(toJsonWebKey()).encodeToByteArray()
        ),
        privateMaterial = false,
    )

internal suspend fun LegacyKey.toCrypto2PublicJwk(): EncodedKey.Jwk =
    EncodedKey.Jwk(
        data = BinaryData(getPublicKey().exportJWK().encodeToByteArray()),
        privateMaterial = false,
    )

internal suspend fun buildCrypto2GenericX509CertificateDer(
    profileData: GenericX509CertificateProfileData,
    subjectPublicKey: Key,
    signingKey: Key,
    signatureAlgorithm: SignatureAlgorithm,
): CertificateDer {
    signatureAlgorithm.requireCompatibleWith(signingKey)
    val subjectSignumPublicKey = subjectPublicKey.toSignumPublicKey()
    val issuerSignumPublicKey = signingKey.toSignumPublicKey()
    val issuerName = profileData.issuerName ?: profileData.subjectName
    val x509SignatureAlgorithm = signatureAlgorithm.toSignumSignatureAlgorithm()
        .toX509SignatureAlgorithm()
        .getOrThrow()

    return buildSignumX509CertificateDer(
        serialNumber = generateIsoCompliantX509CertificateSerialNo(),
        issuerName = issuerName.toSignumName(),
        subjectName = profileData.subjectName.toSignumName(),
        validityPeriod = profileData.validityPeriod,
        subjectPublicKey = subjectSignumPublicKey,
        signatureAlgorithm = x509SignatureAlgorithm,
        extensions = buildList {
            add(subjectKeyIdentifierExtension(subjectSignumPublicKey))
            add(authorityKeyIdentifierExtension(issuerSignumPublicKey))
            add(
                basicConstraintsExtension(
                    isCa = profileData.isCertificateAuthority,
                    pathLengthConstraint = profileData.pathLengthConstraint,
                )
            )
            profileData.keyUsage.takeIf { it.isNotEmpty() }?.let {
                add(keyUsageExtension(it))
            }
            profileData.extendedKeyUsageOids.takeIf { it.isNotEmpty() }?.let {
                add(extendedKeyUsageExtension(it, critical = false))
            }
            profileData.subjectAlternativeNames
                ?.takeUnless { it.isEmpty }
                ?.let { add(subjectAlternativeNamesExtension(it)) }
            profileData.crlDistributionPointUri?.let {
                add(crlDistributionPointExtension(it))
            }
        },
        sign = { signingKey.signX509(it, signatureAlgorithm) },
    )
}

internal suspend fun buildCrypto2IacaCertificateDer(
    profileData: IACACertificateProfileData,
    signingKey: Key,
    signatureAlgorithm: SignatureAlgorithm,
): CertificateDer {
    signatureAlgorithm.requireCompatibleWith(signingKey)
    signingKey.requireIsoEcKey("IACA signing key")
    val publicKey = signingKey.toSignumPublicKey()
    return buildSignumX509CertificateDer(
        serialNumber = generateIsoCompliantX509CertificateSerialNo(),
        issuerName = profileData.principalName.toSignumName(),
        subjectName = profileData.principalName.toSignumName(),
        validityPeriod = profileData.validityPeriod,
        subjectPublicKey = publicKey,
        signatureAlgorithm = signatureAlgorithm.toSignumSignatureAlgorithm().toX509SignatureAlgorithm().getOrThrow(),
        extensions = buildList {
            add(subjectKeyIdentifierExtension(publicKey))
            add(basicConstraintsExtension(isCa = true, pathLengthConstraint = 0))
            add(issuerAlternativeNameExtension(profileData.issuerAlternativeName))
            add(keyUsageExtension(setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign)))
            profileData.crlDistributionPointUri?.let { add(crlDistributionPointExtension(it)) }
        },
        sign = { signingKey.signX509(it, signatureAlgorithm) },
    )
}

internal suspend fun buildCrypto2DocumentSignerCertificateDer(
    profileData: DocumentSignerCertificateProfileData,
    subjectPublicKey: Key,
    iacaProfileData: IACACertificateProfileData,
    signingKey: Key,
    signatureAlgorithm: SignatureAlgorithm,
): CertificateDer {
    signatureAlgorithm.requireCompatibleWith(signingKey)
    subjectPublicKey.requireIsoEcKey("Document Signer public key")
    signingKey.requireIsoEcKey("IACA signing key")
    val subjectSignumPublicKey = subjectPublicKey.toSignumPublicKey()
    val issuerSignumPublicKey = signingKey.toSignumPublicKey()
    return buildSignumX509CertificateDer(
        serialNumber = generateIsoCompliantX509CertificateSerialNo(),
        issuerName = iacaProfileData.principalName.toSignumName(),
        subjectName = profileData.principalName.toSignumName(),
        validityPeriod = profileData.validityPeriod,
        subjectPublicKey = subjectSignumPublicKey,
        signatureAlgorithm = signatureAlgorithm.toSignumSignatureAlgorithm().toX509SignatureAlgorithm().getOrThrow(),
        extensions = listOf(
            authorityKeyIdentifierExtension(issuerSignumPublicKey),
            subjectKeyIdentifierExtension(subjectSignumPublicKey),
            keyUsageExtension(setOf(X509KeyUsage.DigitalSignature)),
            issuerAlternativeNameExtension(iacaProfileData.issuerAlternativeName),
            extendedKeyUsageExtension(setOf(DocumentSignerEkuOID)),
            crlDistributionPointExtension(profileData.crlDistributionPointUri),
        ),
        sign = { signingKey.signX509(it, signatureAlgorithm) },
    )
}

internal suspend fun buildCrypto2CertificateSigningRequestDer(
    profileData: CertificateSigningRequestProfileData,
    signingKey: Key,
    signatureAlgorithm: SignatureAlgorithm,
): CertificateSigningRequestDer {
    signatureAlgorithm.requireCompatibleWith(signingKey)
    val tbsCsr = TbsCertificationRequest(
        subjectName = profileData.subjectName.toSignumName(),
        publicKey = signingKey.toSignumPublicKey(),
        extensions = profileData.subjectAlternativeNames
            ?.takeUnless { it.isEmpty }
            ?.let { listOf(subjectAlternativeNamesExtension(it)) },
    )
    val csr = Pkcs10CertificationRequest(
        tbsCsr = tbsCsr,
        signatureAlgorithm = signatureAlgorithm.toSignumSignatureAlgorithm()
            .toX509SignatureAlgorithm()
            .getOrThrow(),
        signature = signingKey.signX509(tbsCsr.encodeToDer(), signatureAlgorithm),
    )
    return CertificateSigningRequestDer(ByteString(csr.encodeToDer()))
}

private suspend fun Key.toSignumPublicKey(): CryptoPublicKey {
    val encoded = capabilities.publicKeyExporter?.exportPublicKey()
        ?: (this as? ManagedKey)?.storedKey?.publicKey
        ?: throw IllegalArgumentException("X.509 key must support public-key export")
    return when (encoded) {
        is EncodedKey.Jwk -> joseCompliantSerializer
            .decodeFromString<JsonWebKey>(encoded.data.toByteArray().decodeToString())
            .toCryptoPublicKey()
            .getOrThrow()
        is EncodedKey.SpkiDer -> CryptoPublicKey.decodeFromDer(encoded.data.toByteArray())
        is EncodedKey.Pkcs8Der -> throw IllegalArgumentException("X.509 public key export returned private key material")
    }
}

private suspend fun Key.signX509(
    data: ByteArray,
    algorithm: SignatureAlgorithm,
): CryptoSignature {
    val signer = capabilities.signer ?: throw IllegalArgumentException("X.509 signing key does not support signing")
    val signature = signer.sign(data, algorithm)
    return when (algorithm) {
        is SignatureAlgorithm.Ecdsa -> CryptoSignature.EC.decodeFromDer(signature)
        is SignatureAlgorithm.RsaPkcs1,
        is SignatureAlgorithm.RsaPss -> CryptoSignature.RSA(signature)
        else -> throw IllegalArgumentException("Unsupported X.509 signature algorithm: $algorithm")
    }
}

private fun SignatureAlgorithm.requireCompatibleWith(key: Key) {
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
            require(saltLengthBytes == digest.outputSizeBytes()) {
                "X.509 RSA-PSS salt length must be explicit and match the digest length"
            }
        }
        else -> throw IllegalArgumentException("Unsupported X.509 signature algorithm: $this")
    }
}

private fun Key.requireIsoEcKey(name: String) {
    val curve = (spec as? KeySpec.Ec)?.curve
    require(curve == EcCurve.P256 || curve == EcCurve.P384 || curve == EcCurve.P521) {
        "$name must use P-256, P-384, or P-521"
    }
}

private fun SignatureAlgorithm.toSignumSignatureAlgorithm(): SignumSignatureAlgorithm =
    when (this) {
        is SignatureAlgorithm.Ecdsa -> when (digest) {
            DigestAlgorithm.SHA_256 -> SignumSignatureAlgorithm.ECDSAwithSHA256
            DigestAlgorithm.SHA_384 -> SignumSignatureAlgorithm.ECDSAwithSHA384
            DigestAlgorithm.SHA_512 -> SignumSignatureAlgorithm.ECDSAwithSHA512
            else -> throw IllegalArgumentException("Unsupported X.509 ECDSA digest: ${digest.name}")
        }
        is SignatureAlgorithm.RsaPkcs1 -> when (digest) {
            DigestAlgorithm.SHA_256 -> SignumSignatureAlgorithm.RSAwithSHA256andPKCS1Padding
            DigestAlgorithm.SHA_384 -> SignumSignatureAlgorithm.RSAwithSHA384andPKCS1Padding
            DigestAlgorithm.SHA_512 -> SignumSignatureAlgorithm.RSAwithSHA512andPKCS1Padding
            else -> throw IllegalArgumentException("Unsupported X.509 RSA PKCS#1 digest: ${digest.name}")
        }
        is SignatureAlgorithm.RsaPss -> when (digest) {
            DigestAlgorithm.SHA_256 -> SignumSignatureAlgorithm.RSAwithSHA256andPSSPadding
            DigestAlgorithm.SHA_384 -> SignumSignatureAlgorithm.RSAwithSHA384andPSSPadding
            DigestAlgorithm.SHA_512 -> SignumSignatureAlgorithm.RSAwithSHA512andPSSPadding
            else -> throw IllegalArgumentException("Unsupported X.509 RSA-PSS digest: ${digest.name}")
        }
        else -> throw IllegalArgumentException("Unsupported X.509 signature algorithm: $this")
    }

private fun DigestAlgorithm.outputSizeBytes(): Int = when (this) {
    DigestAlgorithm.SHA_256 -> 32
    DigestAlgorithm.SHA_384 -> 48
    DigestAlgorithm.SHA_512 -> 64
    else -> throw IllegalArgumentException("Unsupported X.509 digest: $name")
}
