package id.walt.x509

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.signum.indispensable.requireSupported
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.verifierFor
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.iso.signSignumX509Raw
import id.walt.x509.iso.signumAttributeValue
import id.walt.x509.iso.subjectAlternativeNamesExtension
import id.walt.x509.iso.toSignumName
import id.walt.x509.iso.toSignumPublicKey
import id.walt.x509.iso.toSignumSignatureAlgorithm
import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64

actual suspend fun platformBuildCertificateSigningRequest(
    profileData: CertificateSigningRequestProfileData,
    signingKey: Key,
): CertificateSigningRequestBundle {
    val extensions = profileData.subjectAlternativeNames
        ?.takeUnless { it.isEmpty }
        ?.let { listOf(subjectAlternativeNamesExtension(it)) }
    val tbsCsr = TbsCertificationRequest(
        subjectName = profileData.subjectName.toSignumName(),
        publicKey = signingKey.toSignumPublicKey(),
        extensions = extensions,
    )
    val signatureAlgorithm = signingKey.keyType
        .toSignumSignatureAlgorithm()
        .toX509SignatureAlgorithm()
        .getOrThrow()
    val csr = Pkcs10CertificationRequest(
        tbsCsr = tbsCsr,
        signatureAlgorithm = signatureAlgorithm,
        signature = signingKey.signSignumX509Raw(tbsCsr.encodeToDer()),
    )
    val csrDer = CertificateSigningRequestDer(ByteString(csr.encodeToDer()))
    return CertificateSigningRequestBundle(
        csrDer = csrDer,
        decodedCsr = csr.toDecodedCertificateSigningRequest(),
    )
}

actual suspend fun parseCertificateSigningRequest(
    csrDer: CertificateSigningRequestDer,
): DecodedCertificateSigningRequest {
    val csr = Pkcs10CertificationRequest.decodeFromDer(csrDer.bytes.toByteArray())
    csr.verifySignature()
    return csr.toDecodedCertificateSigningRequest()
}

private suspend fun Pkcs10CertificationRequest.toDecodedCertificateSigningRequest(): DecodedCertificateSigningRequest =
    DecodedCertificateSigningRequest(
        subjectName = tbsCsr.subjectName.toDistinguishedName(),
        subjectAlternativeNames = tbsCsr.requestedSubjectAlternativeNames(),
        publicKey = tbsCsr.publicKey.toWaltPublicKey(),
    )

private fun Pkcs10CertificationRequest.verifySignature() {
    signatureAlgorithm.requireSupported()
    val supportedSignatureAlgorithm = signatureAlgorithm as X509SignatureAlgorithm
    supportedSignatureAlgorithm.algorithm
        .verifierFor(tbsCsr.publicKey)
        .getOrThrow()
        .verify(
            data = SignatureInput(rawTbsCsr.derEncoded),
            sig = decodedSignature.getOrThrow(),
        ).getOrThrow()
}

private suspend fun CryptoPublicKey.toWaltPublicKey(): Key {
    val derBytes = encodeToTlv().derEncoded
    val pem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.Default.encode(derBytes).chunked(64).joinToString("\n") +
            "\n-----END PUBLIC KEY-----"
    return JWKKey.importPEM(pem).getOrThrow().getPublicKey()
}

private fun List<RelativeDistinguishedName>.toDistinguishedName(): X509DistinguishedName =
    X509DistinguishedName(
        commonName = signumAttributeValue("2.5.4.3") ?: toString(),
        country = signumAttributeValue("2.5.4.6"),
        stateOrProvinceName = signumAttributeValue("2.5.4.8"),
        organizationName = signumAttributeValue("2.5.4.10"),
        localityName = signumAttributeValue("2.5.4.7"),
        organizationalUnitName = signumAttributeValue("2.5.4.11"),
    )

private fun TbsCertificationRequest.requestedSubjectAlternativeNames(): X509SubjectAlternativeNames? =
    attributes
        .firstOrNull { it.oid == ObjectIdentifier("1.2.840.113549.1.9.14") }
        ?.value
        ?.firstOrNull()
        ?.asSequence()
        ?.children
        ?.map { X509CertificateExtension.decodeFromTlv(it.asSequence()) }
        ?.firstOrNull { it.oid == ObjectIdentifier("2.5.29.17") }
        ?.value
        ?.asOctetString()
        ?.content
        ?.let { Asn1Element.parse(it).asSequence().children.toSubjectAlternativeNames() }
        ?.takeUnless { it.isEmpty }

private fun List<Asn1Element>.toSubjectAlternativeNames(): X509SubjectAlternativeNames {
    val dnsNames = mutableListOf<String>()
    val uris = mutableListOf<String>()
    val emails = mutableListOf<String>()
    val ipAddresses = mutableListOf<String>()
    forEach { name ->
        when (name.tag) {
            SubjectAltNameImplicitTags.dNSName -> dnsNames.add(name.asPrimitive().content.decodeToString())
            SubjectAltNameImplicitTags.uniformResourceIdentifier -> uris.add(name.asPrimitive().content.decodeToString())
            SubjectAltNameImplicitTags.rfc822Name -> emails.add(name.asPrimitive().content.decodeToString())
            SubjectAltNameImplicitTags.iPAddress -> ipAddresses.add(name.asPrimitive().content.toIpAddressString())
        }
    }
    return X509SubjectAlternativeNames(
        dnsNames = dnsNames,
        uris = uris,
        emails = emails,
        ipAddresses = ipAddresses,
    )
}

private fun ByteArray.toIpAddressString(): String =
    when (size) {
        4 -> joinToString(".") { it.toUByte().toString() }
        16 -> asIterable()
            .chunked(2)
            .joinToString(":") { ((it[0].toInt() and 0xff) shl 8 or (it[1].toInt() and 0xff)).toString(16) }
        else -> error("Invalid IP address subject alternative name byte count: $size")
    }
