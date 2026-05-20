package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.io.StringWriter
import java.net.InetAddress
import java.security.PublicKey

actual suspend fun platformBuildCertificateSigningRequest(
    profileData: CertificateSigningRequestProfileData,
    signingKey: Key,
): CertificateSigningRequestBundle {
    val subjectPublicKey = parsePEMEncodedJcaPublicKey(signingKey.getPublicKey().exportPEM())
    val csrBuilder = JcaPKCS10CertificationRequestBuilder(
        profileData.subjectName.toX500Name(),
        subjectPublicKey,
    )

    profileData.subjectAlternativeNames
        ?.takeUnless { it.isEmpty }
        ?.toGeneralNames()
        ?.let { generalNames ->
            csrBuilder.addAttribute(
                PKCSObjectIdentifiers.pkcs_9_at_extensionRequest,
                Extensions(arrayOf(Extension(Extension.subjectAlternativeName, false, generalNames.encoded))),
            )
        }

    val csr = csrBuilder.build(KeyContentSignerWrapper(signingKey))
    return CertificateSigningRequestBundle(
        csrDer = CertificateSigningRequestDer(ByteString(csr.encoded)),
        decodedCsr = csr.toDecodedCertificateSigningRequest(),
    )
}

actual suspend fun parseCertificateSigningRequest(
    csrDer: CertificateSigningRequestDer,
): DecodedCertificateSigningRequest {
    val csr = PKCS10CertificationRequest(csrDer.bytes.toByteArray())
    val publicKey = csr.subjectPublicKeyInfo.toPublicKey()
    require(csr.isSignatureValid(JcaContentVerifierProviderBuilder().build(publicKey))) {
        "CSR signature is invalid."
    }
    return csr.toDecodedCertificateSigningRequest(publicKey)
}

internal suspend fun PKCS10CertificationRequest.toDecodedCertificateSigningRequest(
    publicKey: PublicKey = subjectPublicKeyInfo.toPublicKey(),
): DecodedCertificateSigningRequest {
    return DecodedCertificateSigningRequest(
        subjectName = subject.toDistinguishedName(),
        subjectAlternativeNames = requestedSubjectAlternativeNames(),
        publicKey = publicKey.toWaltPublicKey(),
    )
}

internal fun X509DistinguishedName.toX500Name(): X500Name {
    val builder = X500NameBuilder(BCStyle.INSTANCE)
    country?.let { builder.addRDN(BCStyle.C, it) }
    stateOrProvinceName?.let { builder.addRDN(BCStyle.ST, it) }
    localityName?.let { builder.addRDN(BCStyle.L, it) }
    organizationName?.let { builder.addRDN(BCStyle.O, it) }
    organizationalUnitName?.let { builder.addRDN(BCStyle.OU, it) }
    builder.addRDN(BCStyle.CN, commonName)
    return builder.build()
}

internal fun X500Name.toDistinguishedName(): X509DistinguishedName {
    fun firstValue(oid: ASN1ObjectIdentifier): String? =
        getRDNs(oid).firstOrNull()?.first?.value?.toString()

    return X509DistinguishedName(
        commonName = firstValue(BCStyle.CN) ?: toString(),
        country = firstValue(BCStyle.C),
        stateOrProvinceName = firstValue(BCStyle.ST),
        organizationName = firstValue(BCStyle.O),
        localityName = firstValue(BCStyle.L),
        organizationalUnitName = firstValue(BCStyle.OU),
    )
}

internal fun X509SubjectAlternativeNames.toGeneralNames(): GeneralNames =
    GeneralNames(
        buildList {
            dnsNames.forEach { add(GeneralName(GeneralName.dNSName, it)) }
            uris.forEach { add(GeneralName(GeneralName.uniformResourceIdentifier, it)) }
            emails.forEach { add(GeneralName(GeneralName.rfc822Name, it)) }
            ipAddresses.forEach { add(GeneralName(GeneralName.iPAddress, it)) }
        }.toTypedArray()
    )

internal fun GeneralNames.toSubjectAlternativeNames(): X509SubjectAlternativeNames {
    val dnsNames = mutableListOf<String>()
    val uris = mutableListOf<String>()
    val emails = mutableListOf<String>()
    val ipAddresses = mutableListOf<String>()
    names.forEach { name ->
        when (name.tagNo) {
            GeneralName.dNSName -> dnsNames.add(name.name.toString())
            GeneralName.uniformResourceIdentifier -> uris.add(name.name.toString())
            GeneralName.rfc822Name -> emails.add(name.name.toString())
            GeneralName.iPAddress -> ipAddresses.add(InetAddress.getByAddress(name.name.toASN1Primitive().encoded).hostAddress)
        }
    }
    return X509SubjectAlternativeNames(
        dnsNames = dnsNames,
        uris = uris,
        emails = emails,
        ipAddresses = ipAddresses,
    )
}

private fun PKCS10CertificationRequest.requestedSubjectAlternativeNames(): X509SubjectAlternativeNames? =
    getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)
        .firstOrNull()
        ?.attrValues
        ?.firstOrNull()
        ?.let { Extensions.getInstance(it) }
        ?.getExtension(Extension.subjectAlternativeName)
        ?.parsedValue
        ?.let { GeneralNames.getInstance(it).toSubjectAlternativeNames() }
        ?.takeUnless { it.isEmpty }

internal suspend fun PublicKey.toWaltPublicKey(): Key {
    val pem = StringWriter().use { sw ->
        JcaPEMWriter(sw).use { writer ->
            writer.writeObject(this)
        }
        sw.toString()
    }
    return JWKKey.importPEM(pem).getOrThrow().getPublicKey()
}

internal fun org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.toPublicKey(): PublicKey {
    val algorithmName = when (algorithm.algorithm.id) {
        "1.2.840.10045.2.1" -> "EC"
        "1.2.840.113549.1.1.1" -> "RSA"
        "1.3.101.112" -> "Ed25519"
        else -> error("Unsupported CSR public key algorithm: ${algorithm.algorithm.id}")
    }
    return java.security.KeyFactory.getInstance(algorithmName)
        .generatePublic(java.security.spec.X509EncodedKeySpec(encoded))
}
