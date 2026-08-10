package id.walt.issuer.services.onboarding

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.did.dids.DidService
import id.walt.issuer.issuance.IssuerOnboardingResponse
import id.walt.issuer.issuance.OnboardingRequest
import id.walt.issuer.services.onboarding.models.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds


object OnboardingService {

    private val iaCaRootCertUtil = X509CertificateUtil {
        addValidators(
            IsoIaCaRootX509CertificateProfile,
            X509CertificateValidityValidator(allowValidityInFuture = true)
        )
    }

    private val documentSignerCertUtil = X509CertificateUtil {
        addValidators(
            IsoDocumentSignerX509CertificateProfile,
            X509CertificateValidityValidator(allowValidityInFuture = true)
        )
    }

    suspend fun onboardIACA(
        request: IACAOnboardingRequest,
    ): IACAOnboardingResponse {

        val iacaKey = KeyManager.createKey(
            generationRequest = request.ecKeyGenRequestParams.toKeyGenerationRequest(),
        )

        val iacaRoot = X509CertificateUtil.createSelfSignedCertificate(iacaKey) {
            profileIaCaRootCertificate(
                issuerDnCountryCode = request.certificateData.country,
                issuerDnStateOrProvinceName = request.certificateData.stateOrProvinceName,
                issuerDnOrganizationName = request.certificateData.organizationName,
                issuerDnCommonName = request.certificateData.commonName,
                issuerDnSerialNumber = null,
                issuerEmailAddress = request.certificateData.issuerAlternativeNameConf.email,
                issuerUri = request.certificateData.issuerAlternativeNameConf.uri
            )
            validity = X509Certificate.Validity(
                notBefore = request.certificateData.finalNotBefore,
                notAfter = request.certificateData.finalNotAfter
            )

            request.certificateData.crlDistributionPointUri?.also { crlUri ->
                extensionCrlDistributionPoints {
                    addUriDistributionPoint(crlUri)
                }
            }
        }

        val validationResult = iaCaRootCertUtil.validateCertificateChain(
            listOf(iacaRoot),
            iacaRoot
        )

        require(validationResult.valid) {
            "Certificate not profile compliant: ${
                validationResult.log.filter { it.severity == ValidationResult.Severity.ERROR }.map { it.message }
            }"
        }
        return IACAOnboardingResponse(
            iacaKey = serializeGeneratedPrivateKeyToJsonObject(
                backend = request.ecKeyGenRequestParams.backend,
                key = iacaKey,
            ),
            certificateData = IACACertificateData(
                country = request.certificateData.country,
                commonName = request.certificateData.commonName,
                notBefore = iacaRoot.data.validity.notBefore,
                notAfter = iacaRoot.data.validity.notAfter,
                issuerAlternativeNameConf = iacaRoot.data.extensionIssuerAltName?.let { extIssAlt ->
                    val mail = extIssAlt.alternativeNames
                        .filter { it.type == GeneralName.NameType.rfc822Name }
                        .map { it.value }
                        .firstOrNull()
                    val uri = extIssAlt.alternativeNames
                        .filter { it.type == GeneralName.NameType.uniformResourceIdentifier }
                        .map { it.value }
                        .firstOrNull()
                    IssuerAlternativeNameConfiguration(mail, uri)
                } ?: error("Mandatory extension Issuer Alternative Name not set"),
                stateOrProvinceName = request.certificateData.stateOrProvinceName,
                organizationName = request.certificateData.organizationName,
                crlDistributionPointUri = iacaRoot.data.extensionCrlDistributionPoints?.distributionPoints
                    ?.flatMap { it.distributionPointFullName ?: emptyList() }
                    ?.filter { it.type == GeneralName.NameType.uniformResourceIdentifier }
                    ?.map { it.value }
                    ?.firstOrNull()
            ),
            certificatePEM = iacaRoot.encodedPem,
            certificateValidationResult = validationResult.log.map {
                IACAOnboardingResponse.CertificateValidationLogLine(it.validatorId, it.severity.name, it.message)
            }
        )
    }

    suspend fun onboardDocumentSigner(
        request: DocumentSignerOnboardingRequest,
    ): DocumentSignerOnboardingResponse {

        require(request.certificateData.issuerEmailAddress != null || request.certificateData.issuerUri != null) {
            "Either issuer email or URI must be set"
        }

        val documentSignerKey = KeyManager.createKey(
            generationRequest = request.ecKeyGenRequestParams.toKeyGenerationRequest(),
        )

        val iacaKey = KeyManager.resolveSerializedKey(request.iacaSigner.iacaKey)
        val iacaCert = X509CertificateUtil.parseCertificatePem(request.iacaSigner.iacaPem)

        require((request.certificateData.finalNotBefore - iacaCert.data.validity.notBefore) > (-1).seconds) {
            "Document signer certificate validity (${request.certificateData.finalNotBefore}) starts before IACA root certificate validity (${iacaCert.data.validity.notBefore}) - ${request.certificateData.finalNotBefore - iacaCert.data.validity.notBefore}"
        }

        require((request.certificateData.finalNotAfter - iacaCert.data.validity.notAfter) < 1.seconds) {
            "Document signer certificate validity (${request.certificateData.finalNotAfter}) ends after IACA root certificate validity (${iacaCert.data.validity.notAfter}) - ${request.certificateData.finalNotAfter - iacaCert.data.validity.notAfter})"
        }

        val documentSingerCert = X509CertificateUtil.createCertificate(iacaKey, iacaCert) {
            profileDocumentSignerCertificate(
                crlDistributionPointUri = request.certificateData.crlDistributionPointUri,
                issuerEmailAddress = request.certificateData.issuerEmailAddress,
                issuerUri = request.certificateData.issuerUri,
                subjectKey = documentSignerKey,
                subjectDnCountryCode = request.certificateData.country,
                subjectDnStateOrProvinceName = request.certificateData.stateOrProvinceName,
                subjectDnLocalityName = request.certificateData.localityName,
                subjectDnOrganizationName = request.certificateData.organizationName,
                subjectDnCommonName = request.certificateData.commonName,
                subjectDnSerialNumber = null
            )
            validity = X509Certificate.Validity(
                notBefore = request.certificateData.finalNotBefore,
                notAfter = request.certificateData.finalNotAfter
            )
        }

        val validationResult = documentSignerCertUtil.validateCertificateChain(
            listOf(documentSingerCert),
            iacaCert
        )

        require(validationResult.valid) {
            "Certificate not profile compliant: ${
                validationResult.log.filter { it.severity == ValidationResult.Severity.ERROR }.map { it.message }
            }"
        }


        return DocumentSignerOnboardingResponse(
            documentSignerKey = serializeGeneratedPrivateKeyToJsonObject(
                backend = request.ecKeyGenRequestParams.backend,
                key = documentSignerKey,
            ),
            certificatePEM = documentSingerCert.encodedPem,
            certificateData = DocumentSignerCertificateData(
                country = request.certificateData.country,
                commonName = request.certificateData.commonName,
                notBefore = documentSingerCert.data.validity.notAfter,
                notAfter = documentSingerCert.data.validity.notAfter,
                crlDistributionPointUri = request.certificateData.crlDistributionPointUri,
                stateOrProvinceName = request.certificateData.stateOrProvinceName,
                organizationName = request.certificateData.organizationName,
                localityName = request.certificateData.localityName,
            ),
            certificateValidationResult = validationResult.log.map {
                IACAOnboardingResponse.CertificateValidationLogLine(it.validatorId, it.severity.name, it.message)
            }
        )
    }

    suspend fun didIssuerOnboard(
        request: OnboardingRequest,
    ): IssuerOnboardingResponse {
        val keyConfig = request.key.config?.mapValues { (key, value) ->
            if (key == "signingKeyPem") {
                JsonPrimitive(value.jsonPrimitive.content.trimIndent().replace(" ", ""))

            } else {
                value
            }
        }

        val keyGenerationRequest = request.key.copy(config = keyConfig?.let { it1 -> JsonObject(it1) })
        val key = KeyManager.createKey(keyGenerationRequest)

        val did = DidService.registerDefaultDidMethodByKey(
            method = request.did.method,
            key = key,
            args = request.did.config?.mapValues {
                it.value.jsonPrimitive
            } ?: emptyMap()).did

        val serializedKey = serializeGeneratedPrivateKeyToJsonObject(
            backend = request.key.backend,
            key = key,
        )

        return IssuerOnboardingResponse(
            issuerKey = serializedKey,
            issuerDid = did,
        )
    }

    private fun serializeGeneratedPrivateKeyToJsonObject(
        backend: String,
        key: Key,
    ): JsonObject {
        return KeySerialization.serializeKeyToJson(key).let { serializedKey ->
            when {
                backend == "jwk" -> {
                    val jsonObject = serializedKey.jsonObject
                    val jwkObject = jsonObject["jwk"] ?: throw IllegalArgumentException(
                        "No JWK key found in serialized key."
                    )
                    val finalJsonObject = jsonObject.toMutableMap().apply {
                        this["jwk"] = jwkObject.jsonObject
                    }.toMap()
                    JsonObject(finalJsonObject)
                }

                else -> {
                    serializedKey.jsonObject
                }
            }
        }
    }

}
