package id.walt.certificate.x509.signum


import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1Time
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.TbsCertificate
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import id.walt.certificate.x509.*
import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.dn.DistinguishedName
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import id.walt.certificate.x509.signum.SignumSignatureAlgorithmUtil.toSignatureAlgorithm
import id.walt.certificate.x509.signum.dn.toSignumDn
import id.walt.certificate.x509.signum.extension.SignumExtensionFactory
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.io.bytestring.isNotEmpty
import at.asitplus.signum.indispensable.X509SignatureAlgorithm as SigX509SignatureAlgorithm
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName as SigRdn
import at.asitplus.signum.indispensable.pki.X509Certificate as SigX509Certificate
import id.walt.crypto.keys.Key as Crypto1Key

class SignumCertificateSigner : X509CertificateSigner, Pkcs10CertificateSigningRequestSigner {

    override suspend fun convertKeyToPublicKeyInfo(key: Key): PublicKeyInfo =
        SignumPublicKeyInfoUtil.publicKeyInfoOfKey(key)

    override suspend fun convertKeyToPublicKeyInfo(key: Crypto1Key): PublicKeyInfo =
        SignumPublicKeyInfoUtil.publicKeyInfoOfKey(key)

    override suspend fun signCertificate(
        issuerKey: Key,
        signatureAlgorithm: SignatureAlgorithm,
        builder: X509CertificateDataBuilder
    ): X509Certificate {
        val authorityPublicKeyInfo = convertKeyToPublicKeyInfo(issuerKey) as SignumPublicKeyInfo
        val sigAlg = X509SigningAlgorithmInfo.ofKey(issuerKey, signatureAlgorithm)
        return signCertificateInternal(authorityPublicKeyInfo, sigAlg, builder) {
            issuerKey.capabilities.signer?.sign(it, signatureAlgorithm) ?: error("Signer not found for key")
        }
    }

    override suspend fun signCertificate(
        issuerKey: Crypto1Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate {
        val authorityPublicKeyInfo = convertKeyToPublicKeyInfo(issuerKey) as SignumPublicKeyInfo
        val sigAlgorithm = X509SigningAlgorithmInfo.ofKey(issuerKey)
        return signCertificateInternal(
            authorityPublicKeyInfo,
            sigAlgorithm,
            builder
        ) {
            issuerKey.signRaw(it) as ByteArray
        }
    }

    private suspend fun signCertificateInternal(
        authorityPublicKeyInfo: SignumPublicKeyInfo,
        sigAlgorithm: X509SigningAlgorithmInfo,
        builder: X509CertificateDataBuilder,
        signRaw: suspend (rawData: ByteArray) -> ByteArray
    ): X509Certificate {
        val subjectDn: List<RelativeDistinguishedName> = builder.signumSubjectDn
        var issuerDn: List<RelativeDistinguishedName> = subjectDn

        val subjectPublicKeyInfo =
            (builder.subjectPublicKeyInfo as X509CertificateDataBuilder.WaltIdKeySubjectPublicKeyInfoBuilder).let {
                if (it.selfSigned) {
                    issuerDn = subjectDn
                    authorityPublicKeyInfo
                } else {
                    require(builder.issuerDnRaw.isNotEmpty()) { "Issuer DN must be set for non-self-signed certificates." }
                    issuerDn = Asn1Element.parse(builder.issuerDnRaw.toByteArray())
                        .asSequence().children.map {
                            SigRdn.decodeFromTlv(it.asSet())
                        }
                    if (it.crypto1key != null) {
                        require(it.key == null) { "Subject key must be of one type, crypto1key and key cannot be set together." }
                        require(it.spki == null) { "Subject key must be of one type, crypto1key and spki cannot be set together." }
                        convertKeyToPublicKeyInfo(it.crypto1key) as SignumPublicKeyInfo
                    } else if (it.key != null) {
                        require(it.spki == null) { "Subject key must be of one type, key and spki cannot be set together." }
                        convertKeyToPublicKeyInfo(it.key) as SignumPublicKeyInfo
                    } else {
                        requireNotNull(it.spki) { "Subject key must be provided for non-self-signed certificates." }
                        SignumPublicKeyInfo.ofDerEncoded(it.spki.encodedDer.toByteArray())
                    }
                }
            }

        // Construct the Certificate Structure (TBSCertificate)
        val signumSigAlgorithm = sigAlgorithm.toSignatureAlgorithm()
        val signumSigAlgorithmDescription = signumSigAlgorithm
            .toX509SignatureAlgorithm()
            .getOrThrow()

        val tbsCertificate = buildCertificateTbs(
            builder,
            issuerDn,
            subjectDn,
            authorityPublicKeyInfo,
            subjectPublicKeyInfo,
            signumSigAlgorithmDescription
        )

        // Sign the payload using the Issuer Private Key
        // Signum abstracts encoding the payload block structure into ASN.1
        val tbsDerBytes: ByteArray = tbsCertificate.encodeToDer()
        val rawSignatureBytes: ByteArray = signRaw(tbsDerBytes)
        val signature = SignumSignatureAlgorithmUtil.evaluateSignature(sigAlgorithm, rawSignatureBytes)

        // 6. Combine the TBS block and Signature into a definitive X509 Certificate
        val certificate = SigX509Certificate(
            tbsCertificate = tbsCertificate,
            signatureAlgorithm = signumSigAlgorithmDescription,
            signature = signature
        )
        return SignumX509Certificate(certificate)
    }

    private fun buildCertificateTbs(
        builder: X509CertificateDataBuilder,
        issuerDn: List<RelativeDistinguishedName>,
        subjectDn: List<RelativeDistinguishedName>,
        authorityPublicKeyInfo: SignumPublicKeyInfo,
        subjectPublicKeyInfo: SignumPublicKeyInfo,
        signumSigAlgorithmDescription: SigX509SignatureAlgorithm
    ): TbsCertificate {
        require(builder.version == 3) { "Only version 3 certificates are supported by Signum" }

        // Configure Validity Timestamps using Asn1Time wrapper
        val notBefore = Asn1Time(builder.validity.notBefore)
        val notAfter = Asn1Time(builder.validity.notAfter)

        //4. Convert extensions
        val extensions = builder.extensions.values.map { value ->
            if (value.oid == SubjectKeyIdentifierExtension.OID) {
                SignumExtensionFactory.createSubjectKeyIdentifierExtension(
                    value,
                    subjectPublicKeyInfo.keyInfo
                )
            } else if (value.oid == AuthorityKeyIdentifierExtension.OID) {
                SignumExtensionFactory.createAuthorityKeyIdentifierExtension(value, authorityPublicKeyInfo.keyInfo)
            } else {
                SignumExtensionFactory.createExtension(value)
            }
        }

        return TbsCertificate(
            version = builder.version - 1,
            serialNumber = builder.serialNumberRaw.toByteArray(),
            signatureAlgorithm = signumSigAlgorithmDescription,
            issuerName = issuerDn,
            validFrom = notBefore,
            validUntil = notAfter,
            subjectName = subjectDn,
            publicKey = subjectPublicKeyInfo.keyInfo,
            issuerUniqueID = null,
            subjectUniqueID = null,
            extensions = extensions
        )
    }

    override suspend fun signCsr(
        holderKey: Key,
        signatureAlgorithm: SignatureAlgorithm,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest {
        val publicKey = (convertKeyToPublicKeyInfo(holderKey) as SignumPublicKeyInfo).keyInfo
        val tbsCsr = buildTbsCsr(csrBuilder, publicKey)

        // 1. Convert the TBS data class to its canonical ASN.1 DER byte structure
        val tbsDerBytes: ByteArray = tbsCsr.encodeToDer()

        // 2. Compute the cryptographic signature
        val rawSignatureBytes: ByteArray = holderKey.capabilities
            .signer
            ?.sign(tbsDerBytes, signatureAlgorithm)
            ?: error("Signer not found for key")

        // 3. Instantiate the appropriate CryptoSignature variant manually.
        // For EC keys (e.g., P-256), use EC.fromRawBytes. For RSA, use CryptoSignature.RSA.
        val sigAlgorithm = X509SigningAlgorithmInfo.ofKey(holderKey, signatureAlgorithm)
        val algorithm = sigAlgorithm.toSignatureAlgorithm()
        val signature = SignumSignatureAlgorithmUtil.evaluateSignature(sigAlgorithm, rawSignatureBytes)

        // 4. Directly construct the finished PKCS#10 Certificate Request
        val encodedSignedCsr = Pkcs10CertificationRequest(
            tbsCsr = tbsCsr,
            signatureAlgorithm = algorithm.toX509SignatureAlgorithm().getOrThrow(),
            signature = signature
        )

        return SignumCsr(encodedSignedCsr)
    }

    override suspend fun signCsr(
        holderKey: Crypto1Key,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest {

        val publicKey = (convertKeyToPublicKeyInfo(holderKey) as SignumPublicKeyInfo).keyInfo
        val tbsCsr = buildTbsCsr(csrBuilder, publicKey)

        // 1. Convert the TBS data class to its canonical ASN.1 DER byte structure
        val tbsDerBytes: ByteArray = tbsCsr.encodeToDer()

        // 2. Compute the cryptographic signature using your JS/External provider
        val rawSignatureBytes: ByteArray = holderKey.signRaw(tbsDerBytes) as ByteArray

        // 3. Instantiate the appropriate CryptoSignature variant manually.
        // For EC keys (e.g., P-256), use EC.fromRawBytes. For RSA, use CryptoSignature.RSA.
        val sigAlgorithm = X509SigningAlgorithmInfo.ofKey(holderKey)
        val algorithm = sigAlgorithm.toSignatureAlgorithm()
        val signature = SignumSignatureAlgorithmUtil.evaluateSignature(sigAlgorithm, rawSignatureBytes)

        // 4. Directly construct the finished PKCS#10 Certificate Request
        val encodedSignedCsr = Pkcs10CertificationRequest(
            tbsCsr = tbsCsr,
            signatureAlgorithm = algorithm.toX509SignatureAlgorithm().getOrThrow(),
            signature = signature
        )

        return SignumCsr(encodedSignedCsr)
    }

    private fun buildTbsCsr(
        csrBuilder: Pkcs10CertificateSigningRequestBuilder,
        publicKeyInfo: CryptoPublicKey
    ): TbsCertificationRequest {
        val subjectDn = DistinguishedName.ofString(csrBuilder.requestedCertificate.subjectDn)
        val extensions = csrBuilder.requestedCertificate.extensions.values.map { value ->
            SignumExtensionFactory.createExtension(value)
        }

        return TbsCertificationRequest(
            subjectName = subjectDn.toSignumDn(),
            publicKey = publicKeyInfo,
            extensions = extensions,
        )
    }

    private val X509CertificateDataBuilder.signumSubjectDn: List<RelativeDistinguishedName>
        get() = if (subjectDnRaw.isNotEmpty()) {
            check(subjectDn.isEmpty()) { "Subject DN can be either set as string or raw, not both: '$subjectDn', '$subjectDnRaw'"}
            Asn1Element.parse(subjectDnRaw.toByteArray())
                .asSequence()
                .children
                .map { RelativeDistinguishedName.decodeFromTlv(it.asSet())}
        } else {
            DistinguishedName.ofString(subjectDn).toSignumDn()
        }
}