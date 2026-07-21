package id.walt.issuer.services.onboarding

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.did.dids.DidService
import id.walt.issuer.issuance.IssuerOnboardingResponse
import id.walt.issuer.issuance.OnboardingRequest
import id.walt.issuer.services.onboarding.models.*
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


object OnboardingService {

    private val iacaRootCertificateValidator = X509SingleCertificateValidator(listOf(IsoIaCaRootX509CertificateProfile))
    private val dsCertificateBuilder = DocumentSignerCertificateBuilder()

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

        val validationResult = iacaRootCertificateValidator.validate(iacaRoot)

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

        val documentSignerKey = KeyManager.createKey(
            generationRequest = request.ecKeyGenRequestParams.toKeyGenerationRequest(),
        )

        val iacaKey = KeyManager.resolveSerializedKey(request.iacaSigner.iacaKey)

        val certBundle = dsCertificateBuilder.build(
            profileData = request.certificateData.toDocumentSignerCertificateProfileData(),
            publicKey = documentSignerKey.getPublicKey(),
            iacaSignerSpec = IACASignerSpecification(
                profileData = request.iacaSigner.certificateData.toIACACertificateProfileData(),
                signingKey = iacaKey,
            ),
        )

        return DocumentSignerOnboardingResponse(
            documentSignerKey = serializeGeneratedPrivateKeyToJsonObject(
                backend = request.ecKeyGenRequestParams.backend,
                key = documentSignerKey,
            ),
            certificatePEM = certBundle.certificateDer.toPEMEncodedString(),
            certificateData = DocumentSignerCertificateData(
                country = certBundle.decodedCertificate.principalName.country,
                commonName = certBundle.decodedCertificate.principalName.commonName,
                notBefore = certBundle.decodedCertificate.validityPeriod.notBefore,
                notAfter = certBundle.decodedCertificate.validityPeriod.notAfter,
                crlDistributionPointUri = certBundle.decodedCertificate.crlDistributionPointUri,
                stateOrProvinceName = certBundle.decodedCertificate.principalName.stateOrProvinceName,
                organizationName = certBundle.decodedCertificate.principalName.organizationName,
                localityName = certBundle.decodedCertificate.principalName.localityName,
            ),
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
