package id.walt.x509

import id.walt.x509.iso.DS_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.IACA_CERT_MAX_VALIDITY_SECONDS
import id.walt.x509.iso.isValidIsoCountryCode
import kotlin.time.Duration

data class X509ProfileValidationResult(
    val isValid: Boolean,
    val issues: List<String> = emptyList(),
) {
    fun requireValid(message: String = "X509 profile validation failed") {
        require(isValid) {
            if (issues.isEmpty()) message else "$message: ${issues.joinToString(separator = "; ")}"
        }
    }
}

fun X509CertificateProfile.validateDefinition(): X509ProfileValidationResult {
    val issues = mutableListOf<String>()

    basicConstraints?.let { constraints ->
        if (constraints.pathLengthConstraint < 0) {
            issues += "Profile '${profileId.value}' basic constraints pathLengthConstraint must not be negative"
        }

        if (!constraints.isCA && constraints.pathLengthConstraint != 0) {
            issues += "Profile '${profileId.value}' end-entity basic constraints pathLengthConstraint must be 0"
        }

        if (constraints.isCA && X509KeyUsage.KeyCertSign !in keyUsages) {
            issues += "Profile '${profileId.value}' CA profile must include keyCertSign usage"
        }

        if (!constraints.isCA) {
            if (X509KeyUsage.KeyCertSign in keyUsages) {
                issues += "Profile '${profileId.value}' end-entity profile must not include keyCertSign usage"
            }
            if (X509KeyUsage.CRLSign in keyUsages) {
                issues += "Profile '${profileId.value}' end-entity profile must not include cRLSign usage"
            }
        }
    }

    validityPolicy?.let { validityPolicy ->
        if (validityPolicy.maximumValidity == Duration.ZERO) {
            issues += "Profile '${profileId.value}' validity policy maximumValidity must be greater than zero"
        }
    }

    validateKnownProfileDefinition(profileId, this, issues)

    return X509ProfileValidationResult(
        isValid = issues.isEmpty(),
        issues = issues,
    )
}

fun X509CertificateBuildData.checkCompatibility(
    profile: X509CertificateProfile,
): X509ProfileValidationResult {
    val issues = mutableListOf<String>()

    validateValidityPeriod(
        profile = profile,
        validityPeriod = validityPeriod,
        issues = issues,
    )
    validateKnownBuildData(
        profileId = profile.profileId,
        subject = subject,
        subjectAlternativeNames = subjectAlternativeNames,
        issuerAlternativeNames = issuerAlternativeNames,
        crlDistributionPointUri = crlDistributionPointUri,
        mode = X509BuildDataValidationMode.Request,
        issues = issues,
    )

    return X509ProfileValidationResult(
        isValid = issues.isEmpty(),
        issues = issues,
    )
}

fun X509IssuedCertificateData.checkCompatibility(
    profile: X509CertificateProfile,
): X509ProfileValidationResult {
    val issues = mutableListOf<String>()

    validateValidityPeriod(
        profile = profile,
        validityPeriod = validityPeriod,
        issues = issues,
    )
    validateKnownBuildData(
        profileId = profile.profileId,
        subject = subject,
        subjectAlternativeNames = subjectAlternativeNames,
        issuerAlternativeNames = issuerAlternativeNames,
        crlDistributionPointUri = crlDistributionPointUri,
        mode = X509BuildDataValidationMode.IssuedCertificate,
        issues = issues,
    )

    profile.basicConstraints?.let { expectedBasicConstraints ->
        if (!isCompatibleIssuedBasicConstraints(basicConstraints, expectedBasicConstraints)) {
            issues += "Issued certificate basic constraints $basicConstraints do not match profile basic constraints $expectedBasicConstraints"
        }
    }

    if (profile.keyUsages.isNotEmpty() && keyUsages != profile.keyUsages) {
        issues += "Issued certificate key usages $keyUsages do not match profile key usages ${profile.keyUsages}"
    }

    if (profile.extendedKeyUsages.isNotEmpty() && extendedKeyUsages != profile.extendedKeyUsages) {
        issues += "Issued certificate extended key usages ${extendedKeyUsages.map { it.oid }.toSet()} do not match profile extended key usages ${profile.extendedKeyUsages.map { it.oid }.toSet()}"
    }

    when (profile.profileId) {
        X509KnownProfileIds.IsoIaca,
        X509KnownProfileIds.GenericCa
            -> if (subject != issuer) {
                issues += "Issued certificate for profile '${profile.profileId.value}' must be self-issued"
            }

        X509KnownProfileIds.IsoDocumentSigner,
        X509KnownProfileIds.GenericEndEntity,
        X509KnownProfileIds.Qwac,
        X509KnownProfileIds.Qsealc,
        X509KnownProfileIds.Psd2Transport
            -> if (subject == issuer) {
                issues += "Issued certificate for profile '${profile.profileId.value}' must not be self-issued"
            }
    }

    return X509ProfileValidationResult(
        isValid = issues.isEmpty(),
        issues = issues,
    )
}

fun X509CertificateIssuanceSpec.checkCompatibility(
    profileResolver: X509CertificateProfileResolver = X509KnownCertificateProfiles.registry,
): X509ProfileValidationResult {
    val profile = profileResolver.resolve(profileId)
        ?: return X509ProfileValidationResult(
            isValid = false,
            issues = listOf("Unsupported X509 profile id: ${profileId.value}"),
        )

    val issues = mutableListOf<String>()
    issues += profile.validateDefinition().issues
    issues += certificateData.checkCompatibility(profile).issues

    when (profileId) {
        X509KnownProfileIds.IsoIaca,
        X509KnownProfileIds.GenericCa
            -> if (this !is X509SelfSignedCertificateIssuanceSpec) {
                issues += "Profile '${profileId.value}' requires a self-signed issuance spec"
            }

        X509KnownProfileIds.IsoDocumentSigner,
        X509KnownProfileIds.GenericEndEntity,
        X509KnownProfileIds.Qwac,
        X509KnownProfileIds.Qsealc,
        X509KnownProfileIds.Psd2Transport
            -> if (this !is X509IssuerSignedCertificateIssuanceSpec) {
                issues += "Profile '${profileId.value}' requires an issuer-signed issuance spec"
            }
    }

    if (this is X509IssuerSignedCertificateIssuanceSpec) {
        validateIssuerSignedCompatibility(
            subjectProfile = profile,
            spec = this,
            profileResolver = profileResolver,
            issues = issues,
        )
    }

    return X509ProfileValidationResult(
        isValid = issues.isEmpty(),
        issues = issues,
    )
}

internal fun validateKnownCsrCompatibility(
    profile: X509CertificateProfile,
    csrData: X509CertificateSigningRequestData,
    issues: MutableList<String>,
) {
    validateKnownBuildData(
        profileId = profile.profileId,
        subject = csrData.subject,
        subjectAlternativeNames = csrData.subjectAlternativeNames,
        issuerAlternativeNames = emptySet(),
        crlDistributionPointUri = null,
        mode = X509BuildDataValidationMode.Request,
        issues = issues,
    )

    if (csrData.keyUsages.isNotEmpty()) {
        val missingRequiredKeyUsages = profile.keyUsages - csrData.keyUsages
        if (missingRequiredKeyUsages.isNotEmpty()) {
            issues += "CSR key usages are missing profile-required usages: ${missingRequiredKeyUsages.joinToString()}"
        }
    }

    if (csrData.extendedKeyUsages.isNotEmpty()) {
        val missingRequiredExtendedKeyUsages = profile.extendedKeyUsages - csrData.extendedKeyUsages
        if (missingRequiredExtendedKeyUsages.isNotEmpty()) {
            issues += "CSR extended key usages are missing profile-required usages: ${missingRequiredExtendedKeyUsages.joinToString { it.oid }}"
        }
    }
}

private fun validateKnownProfileDefinition(
    profileId: X509ProfileId,
    profile: X509CertificateProfile,
    issues: MutableList<String>,
) {
    when (profileId) {
        X509KnownProfileIds.IsoIaca -> {
            expectProfileBasicConstraints(profile, isCA = true, pathLengthConstraint = 0, issues = issues)
            expectExactKeyUsages(profile, setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign), issues)
            expectNoExtendedKeyUsages(profile, issues)
            expectMaximumValidity(profile, IACA_CERT_MAX_VALIDITY_SECONDS, issues)
        }

        X509KnownProfileIds.IsoDocumentSigner -> {
            expectProfileBasicConstraints(profile, isCA = false, pathLengthConstraint = 0, issues = issues)
            expectExactKeyUsages(profile, setOf(X509KeyUsage.DigitalSignature), issues)
            expectExactExtendedKeyUsages(profile, setOf(DocumentSignerEkuOID), issues)
            expectMaximumValidity(profile, DS_CERT_MAX_VALIDITY_SECONDS, issues)
        }

        X509KnownProfileIds.GenericCa -> {
            expectProfileBasicConstraints(profile, isCA = true, pathLengthConstraint = 0, issues = issues)
            expectExactKeyUsages(profile, setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign), issues)
            expectNoExtendedKeyUsages(profile, issues)
        }

        X509KnownProfileIds.GenericEndEntity -> {
            expectProfileBasicConstraints(profile, isCA = false, pathLengthConstraint = 0, issues = issues)
            expectExactKeyUsages(profile, setOf(X509KeyUsage.DigitalSignature), issues)
        }

        X509KnownProfileIds.Qwac -> {
            expectProfileBasicConstraints(profile, isCA = false, pathLengthConstraint = 0, issues = issues)
            expectExactKeyUsages(
                profile,
                setOf(X509KeyUsage.DigitalSignature, X509KeyUsage.KeyEncipherment),
                issues,
            )
            expectExactExtendedKeyUsages(
                profile,
                setOf(X509ExtendedKeyUsage.ServerAuth.oid, X509ExtendedKeyUsage.ClientAuth.oid),
                issues,
            )
        }

        X509KnownProfileIds.Qsealc -> {
            expectProfileBasicConstraints(profile, isCA = false, pathLengthConstraint = 0, issues = issues)
            expectExactKeyUsages(
                profile,
                setOf(X509KeyUsage.DigitalSignature, X509KeyUsage.NonRepudiation),
                issues,
            )
        }

        X509KnownProfileIds.Psd2Transport -> {
            expectProfileBasicConstraints(profile, isCA = false, pathLengthConstraint = 0, issues = issues)
            expectExactKeyUsages(
                profile,
                setOf(X509KeyUsage.DigitalSignature, X509KeyUsage.KeyEncipherment),
                issues,
            )
            expectExactExtendedKeyUsages(
                profile,
                setOf(X509ExtendedKeyUsage.ServerAuth.oid, X509ExtendedKeyUsage.ClientAuth.oid),
                issues,
            )
        }
    }
}

private fun validateIssuerSignedCompatibility(
    subjectProfile: X509CertificateProfile,
    spec: X509IssuerSignedCertificateIssuanceSpec,
    profileResolver: X509CertificateProfileResolver,
    issues: MutableList<String>,
) {
    val issuerProfile = profileResolver.resolve(spec.issuer.profileId)
        ?: run {
            issues += "Unsupported issuer profile id: ${spec.issuer.profileId.value}"
            return
        }

    issues += issuerProfile.validateDefinition().issues

    val issuerValidity = spec.issuer.certificateData.validityPeriod
    val subjectValidity = spec.certificateData.validityPeriod
    if (subjectValidity.notBefore < issuerValidity.notBefore) {
        issues += "Issued certificate notBefore ${subjectValidity.notBefore} must not be before issuer notBefore ${issuerValidity.notBefore}"
    }
    if (subjectValidity.notAfter > issuerValidity.notAfter) {
        issues += "Issued certificate notAfter ${subjectValidity.notAfter} must not be after issuer notAfter ${issuerValidity.notAfter}"
    }

    when (subjectProfile.profileId) {
        X509KnownProfileIds.IsoDocumentSigner -> {
            if (spec.issuer.profileId != X509KnownProfileIds.IsoIaca) {
                issues += "Profile '${subjectProfile.profileId.value}' must be issued by profile '${X509KnownProfileIds.IsoIaca.value}'"
            }

            val issuerCountry = spec.issuer.certificateData.subject.getFirstAttributeValue(X509SubjectAttributeOids.CountryName)
            val subjectCountry = spec.certificateData.subject.getFirstAttributeValue(X509SubjectAttributeOids.CountryName)
            if (issuerCountry != null && subjectCountry != null && issuerCountry != subjectCountry) {
                issues += "ISO document signer country must match issuer IACA country"
            }

            val issuerState = spec.issuer.certificateData.subject.getFirstAttributeValue(X509SubjectAttributeOids.StateOrProvinceName)
            val subjectState = spec.certificateData.subject.getFirstAttributeValue(X509SubjectAttributeOids.StateOrProvinceName)
            if (issuerState != subjectState) {
                issues += "ISO document signer stateOrProvinceName must match issuer IACA stateOrProvinceName"
            }
        }

        X509KnownProfileIds.GenericEndEntity -> if (spec.issuer.profileId != X509KnownProfileIds.GenericCa) {
            issues += "Profile '${subjectProfile.profileId.value}' must be issued by profile '${X509KnownProfileIds.GenericCa.value}'"
        }

        else -> Unit
    }
}

private fun validateValidityPeriod(
    profile: X509CertificateProfile,
    validityPeriod: X509ValidityPeriod,
    issues: MutableList<String>,
) {
    if (validityPeriod.notBefore >= validityPeriod.notAfter) {
        issues += "Certificate validity period notBefore must be before notAfter"
    }

    profile.validityPolicy?.let { validityPolicy ->
        if (validityPeriod.notAfter - validityPeriod.notBefore > validityPolicy.maximumValidity) {
            issues += "Certificate validity exceeds profile maximum validity of ${validityPolicy.maximumValidity}"
        }
    }
}

private enum class X509BuildDataValidationMode {
    Request,
    IssuedCertificate,
}

private fun validateKnownBuildData(
    profileId: X509ProfileId,
    subject: X509Subject,
    subjectAlternativeNames: Set<X509SubjectAlternativeName>,
    issuerAlternativeNames: Set<X509SubjectAlternativeName>,
    crlDistributionPointUri: String?,
    mode: X509BuildDataValidationMode,
    issues: MutableList<String>,
) {
    when (profileId) {
        X509KnownProfileIds.IsoIaca -> {
            validateSubjectShape(
                subject = subject,
                allowedOids = setOf(
                    X509SubjectAttributeOids.CountryName,
                    X509SubjectAttributeOids.CommonName,
                    X509SubjectAttributeOids.StateOrProvinceName,
                    X509SubjectAttributeOids.OrganizationName,
                ),
                requiredOids = setOf(
                    X509SubjectAttributeOids.CountryName,
                    X509SubjectAttributeOids.CommonName,
                ),
                singleValuedOids = setOf(
                    X509SubjectAttributeOids.CountryName,
                    X509SubjectAttributeOids.CommonName,
                    X509SubjectAttributeOids.StateOrProvinceName,
                    X509SubjectAttributeOids.OrganizationName,
                ),
                profileId = profileId,
                issues = issues,
            )
            validateIsoCountryIfPresent(subject, profileId, issues)
            if (subjectAlternativeNames.isNotEmpty()) {
                issues += "Profile '${profileId.value}' does not support subject alternative names"
            }
            when (mode) {
                X509BuildDataValidationMode.Request,
                X509BuildDataValidationMode.IssuedCertificate,
                    -> {
                        validateIssuerAlternativeNames(profileId, issuerAlternativeNames, issues)
                        if (crlDistributionPointUri != null && crlDistributionPointUri.isBlank()) {
                            issues += "Profile '${profileId.value}' CRL distribution point URI must not be blank"
                        }
                    }
            }
        }

        X509KnownProfileIds.IsoDocumentSigner -> {
            validateSubjectShape(
                subject = subject,
                allowedOids = setOf(
                    X509SubjectAttributeOids.CountryName,
                    X509SubjectAttributeOids.CommonName,
                    X509SubjectAttributeOids.StateOrProvinceName,
                    X509SubjectAttributeOids.OrganizationName,
                    X509SubjectAttributeOids.LocalityName,
                ),
                requiredOids = setOf(
                    X509SubjectAttributeOids.CountryName,
                    X509SubjectAttributeOids.CommonName,
                ),
                singleValuedOids = setOf(
                    X509SubjectAttributeOids.CountryName,
                    X509SubjectAttributeOids.CommonName,
                    X509SubjectAttributeOids.StateOrProvinceName,
                    X509SubjectAttributeOids.OrganizationName,
                    X509SubjectAttributeOids.LocalityName,
                ),
                profileId = profileId,
                issues = issues,
            )
            validateIsoCountryIfPresent(subject, profileId, issues)
            if (subjectAlternativeNames.isNotEmpty()) {
                issues += "Profile '${profileId.value}' does not support subject alternative names"
            }
            when (mode) {
                X509BuildDataValidationMode.Request -> {
                    if (issuerAlternativeNames.isNotEmpty()) {
                        issues += "Profile '${profileId.value}' does not accept issuer alternative names in certificate build data"
                    }
                }

                X509BuildDataValidationMode.IssuedCertificate -> {
                    if (issuerAlternativeNames.isNotEmpty()) {
                        validateIssuerAlternativeNames(profileId, issuerAlternativeNames, issues)
                    }
                }
            }
            if (crlDistributionPointUri.isNullOrBlank()) {
                issues += "Profile '${profileId.value}' requires a non-blank CRL distribution point URI"
            }
        }

        X509KnownProfileIds.Qwac,
        X509KnownProfileIds.Psd2Transport
            -> {
                if (subjectAlternativeNames.none {
                        it is X509SubjectAlternativeName.DnsName || it is X509SubjectAlternativeName.IpAddress
                    }) {
                    issues += "Profile '${profileId.value}' requires at least one DNS or IP subject alternative name"
                }
            }

        else -> Unit
    }
}

private fun validateSubjectShape(
    subject: X509Subject,
    allowedOids: Set<String>,
    requiredOids: Set<String>,
    singleValuedOids: Set<String>,
    profileId: X509ProfileId,
    issues: MutableList<String>,
) {
    val presentOids = subject.attributes.map { it.oid }
    val unsupportedOids = presentOids.filterNot { it in allowedOids }
    if (unsupportedOids.isNotEmpty()) {
        issues += "Profile '${profileId.value}' does not support subject OIDs: ${unsupportedOids.distinct().joinToString()}"
    }

    requiredOids.forEach { oid ->
        if (subject.getAttributeValues(oid).isEmpty()) {
            issues += "Profile '${profileId.value}' requires subject attribute '$oid'"
        }
    }

    singleValuedOids.forEach { oid ->
        val count = subject.getAttributeValues(oid).size
        if (count > 1) {
            issues += "Profile '${profileId.value}' allows at most one subject attribute value for OID '$oid'"
        }
    }
}

private fun validateIsoCountryIfPresent(
    subject: X509Subject,
    profileId: X509ProfileId,
    issues: MutableList<String>,
) {
    subject.getFirstAttributeValue(X509SubjectAttributeOids.CountryName)?.let { country ->
        if (!isValidIsoCountryCode(country)) {
            issues += "Profile '${profileId.value}' requires a valid uppercase ISO 3166-1 alpha-2 country code"
        }
    }
}

private fun isCompatibleIssuedBasicConstraints(
    actual: X509BasicConstraints?,
    expected: X509BasicConstraints,
): Boolean {
    if (actual == null) return false
    if (actual == expected) return true

    if (!expected.isCA && !actual.isCA) {
        return true
    }

    return false
}

private fun validateIssuerAlternativeNames(
    profileId: X509ProfileId,
    issuerAlternativeNames: Set<X509SubjectAlternativeName>,
    issues: MutableList<String>,
) {
    val unsupportedNames = issuerAlternativeNames.filterNot {
        it is X509SubjectAlternativeName.Uri || it is X509SubjectAlternativeName.EmailAddress
    }
    if (unsupportedNames.isNotEmpty()) {
        issues += "Profile '${profileId.value}' issuer alternative names only support URI and email address entries"
    }

    val uriCount = issuerAlternativeNames.count { it is X509SubjectAlternativeName.Uri }
    val emailCount = issuerAlternativeNames.count { it is X509SubjectAlternativeName.EmailAddress }
    if (uriCount > 1) {
        issues += "Profile '${profileId.value}' supports at most one issuer alternative name URI entry"
    }
    if (emailCount > 1) {
        issues += "Profile '${profileId.value}' supports at most one issuer alternative name email entry"
    }
    if (uriCount + emailCount == 0) {
        issues += "Profile '${profileId.value}' requires at least one issuer alternative name URI or email entry"
    }
}

private fun expectProfileBasicConstraints(
    profile: X509CertificateProfile,
    isCA: Boolean,
    pathLengthConstraint: Int,
    issues: MutableList<String>,
) {
    if (profile.basicConstraints != X509BasicConstraints(isCA = isCA, pathLengthConstraint = pathLengthConstraint)) {
        issues += "Profile '${profile.profileId.value}' basic constraints must be isCA=$isCA and pathLengthConstraint=$pathLengthConstraint"
    }
}

private fun expectExactKeyUsages(
    profile: X509CertificateProfile,
    expected: Set<X509KeyUsage>,
    issues: MutableList<String>,
) {
    if (profile.keyUsages != expected) {
        issues += "Profile '${profile.profileId.value}' key usages must equal $expected"
    }
}

private fun expectNoExtendedKeyUsages(
    profile: X509CertificateProfile,
    issues: MutableList<String>,
) {
    if (profile.extendedKeyUsages.isNotEmpty()) {
        issues += "Profile '${profile.profileId.value}' must not define extended key usages"
    }
}

private fun expectExactExtendedKeyUsages(
    profile: X509CertificateProfile,
    expectedOids: Set<String>,
    issues: MutableList<String>,
) {
    val actualOids = profile.extendedKeyUsages.map { it.oid }.toSet()
    if (actualOids != expectedOids) {
        issues += "Profile '${profile.profileId.value}' extended key usages must equal $expectedOids"
    }
}

private fun expectMaximumValidity(
    profile: X509CertificateProfile,
    expectedSeconds: Long,
    issues: MutableList<String>,
) {
    val maximumValidity = profile.validityPolicy?.maximumValidity?.inWholeSeconds
    if (maximumValidity != expectedSeconds) {
        issues += "Profile '${profile.profileId.value}' maximum validity must be $expectedSeconds seconds"
    }
}
