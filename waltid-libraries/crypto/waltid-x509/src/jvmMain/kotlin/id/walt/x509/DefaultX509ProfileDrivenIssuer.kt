package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.documentsigner.parser.DocumentSignerCertificateParser
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.iaca.parser.IACACertificateParser
import id.walt.x509.iso.IssuerAlternativeName
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.time.Instant

class DefaultX509ProfileDrivenIssuer(
    private val profileResolver: X509CertificateProfileResolver = X509KnownCertificateProfiles.registry,
) : X509ProfileDrivenIssuer {

    private val iacaCertificateBuilder = IACACertificateBuilder()
    private val documentSignerCertificateBuilder = DocumentSignerCertificateBuilder()
    private val iacaCertificateParser = IACACertificateParser()
    private val documentSignerCertificateParser = DocumentSignerCertificateParser()

    override suspend fun issue(spec: X509CertificateIssuanceSpec): X509IssuedCertificateBundle {
        val profile = profileResolver.resolve(spec.profileId)
            ?: throw IllegalArgumentException("Unsupported X509 profile id: ${spec.profileId.value}")

        return when (spec.profileId) {
            X509KnownProfileIds.IsoIaca -> issueIsoIaca(profile, spec)
            X509KnownProfileIds.IsoDocumentSigner -> issueIsoDocumentSigner(profile, spec)
            X509KnownProfileIds.GenericCa -> issueGenericCa(profile, spec)
            X509KnownProfileIds.GenericEndEntity -> issueGenericEndEntity(profile, spec)
            else -> throw IllegalArgumentException("Unsupported X509 profile id: ${spec.profileId.value}")
        }
    }

    private suspend fun issueIsoIaca(
        profile: X509CertificateProfile,
        spec: X509CertificateIssuanceSpec,
    ): X509IssuedCertificateBundle {
        // Keep the ISO-specific builder/parser path authoritative until parity tests justify any deprecation.
        require(spec is X509SelfSignedCertificateIssuanceSpec) {
            "Profile '${profile.profileId.value}' requires a self-signed issuance spec"
        }

        val certificateData = spec.certificateData
        val principalName = certificateData.subject.toIacaPrincipalName()
        val issuerAlternativeName = certificateData.issuerAlternativeNames.toIssuerAlternativeName()

        val bundle = iacaCertificateBuilder.build(
            profileData = IACACertificateProfileData(
                principalName = principalName,
                validityPeriod = certificateData.validityPeriod,
                issuerAlternativeName = issuerAlternativeName,
                crlDistributionPointUri = certificateData.crlDistributionPointUri,
            ),
            signingKey = spec.signingKey,
        )
        val decodedCertificate = iacaCertificateParser.parse(bundle.certificateDer)

        return X509IssuedCertificateBundle(
            profile = profile,
            certificateDer = bundle.certificateDer,
            certificateData = decodedCertificate.toIssuedCertificateData(),
        )
    }

    private suspend fun issueIsoDocumentSigner(
        profile: X509CertificateProfile,
        spec: X509CertificateIssuanceSpec,
    ): X509IssuedCertificateBundle {
        // Keep the ISO-specific builder/parser path authoritative until parity tests justify any deprecation.
        require(spec is X509IssuerSignedCertificateIssuanceSpec) {
            "Profile '${profile.profileId.value}' requires an issuer-signed issuance spec"
        }
        require(spec.issuer.profileId == X509KnownProfileIds.IsoIaca) {
            "Profile '${profile.profileId.value}' must be issued by profile '${X509KnownProfileIds.IsoIaca.value}'"
        }

        val subjectPrincipal = spec.certificateData.subject.toDocumentSignerPrincipalName()
        val issuerPrincipal = spec.issuer.certificateData.subject.toIacaPrincipalName()
        val issuerAlternativeName = spec.issuer.certificateData.issuerAlternativeNames.toIssuerAlternativeName()

        val bundle = documentSignerCertificateBuilder.build(
            profileData = DocumentSignerCertificateProfileData(
                principalName = subjectPrincipal,
                validityPeriod = spec.certificateData.validityPeriod,
                crlDistributionPointUri = spec.certificateData.crlDistributionPointUri
                    ?: throw IllegalArgumentException("Profile '${profile.profileId.value}' requires crlDistributionPointUri"),
            ),
            publicKey = spec.publicKey,
            iacaSignerSpec = IACASignerSpecification(
                profileData = IACACertificateProfileData(
                    principalName = issuerPrincipal,
                    validityPeriod = spec.issuer.certificateData.validityPeriod,
                    issuerAlternativeName = issuerAlternativeName,
                    crlDistributionPointUri = spec.issuer.certificateData.crlDistributionPointUri,
                ),
                signingKey = spec.issuer.signingKey,
            ),
        )
        val decodedCertificate = documentSignerCertificateParser.parse(bundle.certificateDer)

        return X509IssuedCertificateBundle(
            profile = profile,
            certificateDer = bundle.certificateDer,
            certificateData = decodedCertificate.toIssuedCertificateData(
                profile = profile,
            ),
        )
    }

    private suspend fun issueGenericCa(
        profile: X509CertificateProfile,
        spec: X509CertificateIssuanceSpec,
    ): X509IssuedCertificateBundle {
        require(spec is X509SelfSignedCertificateIssuanceSpec) {
            "Profile '${profile.profileId.value}' requires a self-signed issuance spec"
        }

        val certificate = issueGenericCertificate(
            profile = profile,
            subject = spec.certificateData.subject,
            issuer = spec.certificateData.subject,
            validityPeriod = spec.certificateData.validityPeriod,
            subjectPublicKey = spec.signingKey.getPublicKey(),
            signingKey = spec.signingKey,
            subjectAlternativeNames = spec.certificateData.subjectAlternativeNames,
            issuerAlternativeNames = spec.certificateData.issuerAlternativeNames,
            crlDistributionPointUri = spec.certificateData.crlDistributionPointUri,
            authorityPublicKey = spec.signingKey.getPublicKey(),
        )

        return X509IssuedCertificateBundle(
            profile = profile,
            certificateDer = CertificateDer(certificate.encoded.toByteString()),
            certificateData = X509IssuedCertificateData(
                subject = spec.certificateData.subject,
                issuer = spec.certificateData.subject,
                validityPeriod = spec.certificateData.validityPeriod,
                subjectAlternativeNames = spec.certificateData.subjectAlternativeNames,
                issuerAlternativeNames = spec.certificateData.issuerAlternativeNames,
                keyUsages = profile.keyUsages,
                extendedKeyUsages = profile.extendedKeyUsages,
                basicConstraints = profile.basicConstraints,
                crlDistributionPointUri = spec.certificateData.crlDistributionPointUri,
            ),
        )
    }

    private suspend fun issueGenericEndEntity(
        profile: X509CertificateProfile,
        spec: X509CertificateIssuanceSpec,
    ): X509IssuedCertificateBundle {
        require(spec is X509IssuerSignedCertificateIssuanceSpec) {
            "Profile '${profile.profileId.value}' requires an issuer-signed issuance spec"
        }
        require(spec.issuer.profileId == X509KnownProfileIds.GenericCa) {
            "Profile '${profile.profileId.value}' must be issued by profile '${X509KnownProfileIds.GenericCa.value}'"
        }

        val certificate = issueGenericCertificate(
            profile = profile,
            subject = spec.certificateData.subject,
            issuer = spec.issuer.certificateData.subject,
            validityPeriod = spec.certificateData.validityPeriod,
            subjectPublicKey = spec.publicKey,
            signingKey = spec.issuer.signingKey,
            subjectAlternativeNames = spec.certificateData.subjectAlternativeNames,
            issuerAlternativeNames = spec.certificateData.issuerAlternativeNames,
            crlDistributionPointUri = spec.certificateData.crlDistributionPointUri,
            authorityPublicKey = spec.issuer.signingKey.getPublicKey(),
        )

        return X509IssuedCertificateBundle(
            profile = profile,
            certificateDer = CertificateDer(certificate.encoded.toByteString()),
            certificateData = X509IssuedCertificateData(
                subject = spec.certificateData.subject,
                issuer = spec.issuer.certificateData.subject,
                validityPeriod = spec.certificateData.validityPeriod,
                subjectAlternativeNames = spec.certificateData.subjectAlternativeNames,
                issuerAlternativeNames = spec.certificateData.issuerAlternativeNames,
                keyUsages = profile.keyUsages,
                extendedKeyUsages = profile.extendedKeyUsages,
                basicConstraints = profile.basicConstraints,
                crlDistributionPointUri = spec.certificateData.crlDistributionPointUri,
            ),
        )
    }

    private suspend fun issueGenericCertificate(
        profile: X509CertificateProfile,
        subject: X509Subject,
        issuer: X509Subject,
        validityPeriod: X509ValidityPeriod,
        subjectPublicKey: Key,
        signingKey: Key,
        subjectAlternativeNames: Set<X509SubjectAlternativeName>,
        issuerAlternativeNames: Set<X509SubjectAlternativeName>,
        crlDistributionPointUri: String?,
        authorityPublicKey: Key,
    ): X509Certificate {
        val subjectJavaPublicKey = parsePEMEncodedJcaPublicKey(subjectPublicKey.exportPEM())
        val authorityJavaPublicKey = parsePEMEncodedJcaPublicKey(authorityPublicKey.exportPEM())

        val certificateBuilder = JcaX509v3CertificateBuilder(
            issuer.toJcaX500Name(),
            BigInteger(160, java.security.SecureRandom()),
            Date(Instant.fromEpochSeconds(validityPeriod.notBefore.epochSeconds).toEpochMilliseconds()),
            Date(Instant.fromEpochSeconds(validityPeriod.notAfter.epochSeconds).toEpochMilliseconds()),
            subject.toJcaX500Name(),
            subjectJavaPublicKey,
        )

        val extensionUtils = JcaX509ExtensionUtils()
        certificateBuilder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extensionUtils.createSubjectKeyIdentifier(subjectJavaPublicKey),
        )
        certificateBuilder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extensionUtils.createAuthorityKeyIdentifier(authorityJavaPublicKey),
        )

        profile.basicConstraints?.let { basicConstraints ->
            certificateBuilder.addExtension(
                Extension.basicConstraints,
                true,
                if (basicConstraints.isCA) {
                    BasicConstraints(basicConstraints.pathLengthConstraint)
                } else {
                    BasicConstraints(false)
                },
            )
        }

        if (profile.keyUsages.isNotEmpty()) {
            certificateBuilder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(profile.keyUsages.toBcKeyUsageMask()),
            )
        }

        if (profile.extendedKeyUsages.isNotEmpty()) {
            certificateBuilder.addExtension(
                Extension.extendedKeyUsage,
                true,
                ExtendedKeyUsage(
                    profile.extendedKeyUsages.map {
                        KeyPurposeId.getInstance(ASN1ObjectIdentifier(it.oid))
                    }.toTypedArray()
                ),
            )
        }

        if (subjectAlternativeNames.isNotEmpty()) {
            certificateBuilder.addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames(subjectAlternativeNames.toGeneralNameArray()),
            )
        }

        if (issuerAlternativeNames.isNotEmpty()) {
            certificateBuilder.addExtension(
                Extension.issuerAlternativeName,
                false,
                GeneralNames(issuerAlternativeNames.toGeneralNameArray()),
            )
        }

        crlDistributionPointUri?.let { uri ->
            certificateBuilder.addExtension(
                Extension.cRLDistributionPoints,
                false,
                CRLDistPoint(
                    arrayOf(
                        DistributionPoint(
                            DistributionPointName(
                                GeneralNames(
                                    GeneralName(
                                        GeneralName.uniformResourceIdentifier,
                                        uri,
                                    )
                                )
                            ),
                            null,
                            null,
                        )
                    )
                )
            )
        }

        val certificateHolder = certificateBuilder.build(
            KeyContentSignerWrapper(signingKey),
        )
        return JcaX509CertificateConverter().getCertificate(certificateHolder)
    }
}

private fun X509Subject.toJcaX500Name(): X500Name {
    val builder = X500NameBuilder(BCStyle.INSTANCE)
    attributes.forEach { attribute ->
        builder.addRDN(ASN1ObjectIdentifier(attribute.oid), attribute.value)
    }
    return builder.build()
}

private fun X509Subject.toIacaPrincipalName(): IACAPrincipalName {
    val unsupportedAttributeOids = attributes.map { it.oid }.filterNot {
        it in setOf(
            X509SubjectAttributeOids.CountryName,
            X509SubjectAttributeOids.CommonName,
            X509SubjectAttributeOids.StateOrProvinceName,
            X509SubjectAttributeOids.OrganizationName,
        )
    }
    require(unsupportedAttributeOids.isEmpty()) {
        "ISO IACA profile does not support subject OIDs: ${unsupportedAttributeOids.joinToString()}"
    }
    return IACAPrincipalName(
        country = getRequiredSingleValue(X509SubjectAttributeOids.CountryName),
        commonName = getRequiredSingleValue(X509SubjectAttributeOids.CommonName),
        stateOrProvinceName = getOptionalSingleValue(X509SubjectAttributeOids.StateOrProvinceName),
        organizationName = getOptionalSingleValue(X509SubjectAttributeOids.OrganizationName),
    )
}

private fun X509Subject.toDocumentSignerPrincipalName(): DocumentSignerPrincipalName {
    val unsupportedAttributeOids = attributes.map { it.oid }.filterNot {
        it in setOf(
            X509SubjectAttributeOids.CountryName,
            X509SubjectAttributeOids.CommonName,
            X509SubjectAttributeOids.StateOrProvinceName,
            X509SubjectAttributeOids.OrganizationName,
            X509SubjectAttributeOids.LocalityName,
        )
    }
    require(unsupportedAttributeOids.isEmpty()) {
        "ISO document signer profile does not support subject OIDs: ${unsupportedAttributeOids.joinToString()}"
    }
    return DocumentSignerPrincipalName(
        country = getRequiredSingleValue(X509SubjectAttributeOids.CountryName),
        commonName = getRequiredSingleValue(X509SubjectAttributeOids.CommonName),
        stateOrProvinceName = getOptionalSingleValue(X509SubjectAttributeOids.StateOrProvinceName),
        organizationName = getOptionalSingleValue(X509SubjectAttributeOids.OrganizationName),
        localityName = getOptionalSingleValue(X509SubjectAttributeOids.LocalityName),
    )
}

private fun X509Subject.getRequiredSingleValue(oid: String): String {
    val values = getAttributeValues(oid)
    require(values.size == 1) { "Expected exactly one subject value for OID '$oid', but found ${values.size}" }
    return values.single()
}

private fun X509Subject.getOptionalSingleValue(oid: String): String? {
    val values = getAttributeValues(oid)
    require(values.size <= 1) { "Expected at most one subject value for OID '$oid', but found ${values.size}" }
    return values.singleOrNull()
}

private fun IACAPrincipalName.toX509Subject() = x509SubjectOf(
    X509SubjectAttributes.country(country),
    X509SubjectAttributes.commonName(commonName),
    *listOfNotNull(
        stateOrProvinceName?.let(X509SubjectAttributes::stateOrProvince),
        organizationName?.let(X509SubjectAttributes::organization),
    ).toTypedArray(),
)

private fun DocumentSignerPrincipalName.toX509Subject() = x509SubjectOf(
    X509SubjectAttributes.country(country),
    X509SubjectAttributes.commonName(commonName),
    *listOfNotNull(
        stateOrProvinceName?.let(X509SubjectAttributes::stateOrProvince),
        organizationName?.let(X509SubjectAttributes::organization),
        localityName?.let(X509SubjectAttributes::locality),
    ).toTypedArray(),
)

private fun Set<X509SubjectAlternativeName>.toIssuerAlternativeName(): IssuerAlternativeName {
    val unsupportedNames = filterNot {
        it is X509SubjectAlternativeName.Uri || it is X509SubjectAlternativeName.EmailAddress
    }
    require(unsupportedNames.isEmpty()) {
        "Issuer alternative name only supports URI and email address entries"
    }
    val uriValues = filterIsInstance<X509SubjectAlternativeName.Uri>().map { it.value }
    val emailValues = filterIsInstance<X509SubjectAlternativeName.EmailAddress>().map { it.value }
    require(uriValues.size <= 1) { "Issuer alternative name supports at most one URI value" }
    require(emailValues.size <= 1) { "Issuer alternative name supports at most one email value" }
    require(uriValues.isNotEmpty() || emailValues.isNotEmpty()) {
        "Issuer alternative name requires at least one URI or email value"
    }
    return IssuerAlternativeName(
        uri = uriValues.singleOrNull(),
        email = emailValues.singleOrNull(),
    )
}

private fun IssuerAlternativeName.toSubjectAlternativeNames(): Set<X509SubjectAlternativeName> = buildSet {
    uri?.let { add(X509SubjectAlternativeName.Uri(it)) }
    email?.let { add(X509SubjectAlternativeName.EmailAddress(it)) }
}

private fun IACADecodedCertificate.toIssuedCertificateData(
) = X509IssuedCertificateData(
    subject = principalName.toX509Subject(),
    issuer = principalName.toX509Subject(),
    validityPeriod = validityPeriod,
    issuerAlternativeNames = issuerAlternativeName.toSubjectAlternativeNames(),
    keyUsages = keyUsage,
    basicConstraints = basicConstraints,
    crlDistributionPointUri = crlDistributionPointUri,
)

private fun DocumentSignerDecodedCertificate.toIssuedCertificateData(
    profile: X509CertificateProfile,
) = X509IssuedCertificateData(
    subject = principalName.toX509Subject(),
    issuer = issuerPrincipalName.toX509Subject(),
    validityPeriod = validityPeriod,
    issuerAlternativeNames = issuerAlternativeName.toSubjectAlternativeNames(),
    keyUsages = keyUsage,
    extendedKeyUsages = extendedKeyUsage.map { extendedKeyUsageOid ->
        profile.extendedKeyUsages.find { it.oid == extendedKeyUsageOid }
            ?: X509ExtendedKeyUsage(extendedKeyUsageOid)
    }.toSet(),
    basicConstraints = basicConstraints,
    crlDistributionPointUri = crlDistributionPointUri,
)

private fun Set<X509SubjectAlternativeName>.toGeneralNameArray(): Array<GeneralName> =
    map { alternativeName ->
        when (alternativeName) {
            is X509SubjectAlternativeName.DnsName ->
                GeneralName(GeneralName.dNSName, alternativeName.value)

            is X509SubjectAlternativeName.Uri ->
                GeneralName(GeneralName.uniformResourceIdentifier, alternativeName.value)

            is X509SubjectAlternativeName.EmailAddress ->
                GeneralName(GeneralName.rfc822Name, alternativeName.value)

            is X509SubjectAlternativeName.IpAddress ->
                GeneralName(GeneralName.iPAddress, alternativeName.value)

            is X509SubjectAlternativeName.RegisteredId ->
                GeneralName(GeneralName.registeredID, alternativeName.value)

            is X509SubjectAlternativeName.OtherName -> {
                val vector = ASN1EncodableVector().apply {
                    add(ASN1ObjectIdentifier(alternativeName.typeId))
                    add(DERTaggedObject(true, 0, DERUTF8String(alternativeName.value)))
                }
                GeneralName(GeneralName.otherName, DERSequence(vector))
            }
        }
    }.toTypedArray()

private fun Set<X509KeyUsage>.toBcKeyUsageMask(): Int = fold(0) { acc, usage ->
    acc or when (usage) {
        X509KeyUsage.DigitalSignature -> KeyUsage.digitalSignature
        X509KeyUsage.NonRepudiation -> KeyUsage.nonRepudiation
        X509KeyUsage.KeyEncipherment -> KeyUsage.keyEncipherment
        X509KeyUsage.DataEncipherment -> KeyUsage.dataEncipherment
        X509KeyUsage.KeyAgreement -> KeyUsage.keyAgreement
        X509KeyUsage.KeyCertSign -> KeyUsage.keyCertSign
        X509KeyUsage.CRLSign -> KeyUsage.cRLSign
        X509KeyUsage.EncipherOnly -> KeyUsage.encipherOnly
        X509KeyUsage.DecipherOnly -> KeyUsage.decipherOnly
    }
}
