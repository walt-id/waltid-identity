@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.services.onboarding

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.did.dids.DidService
import id.walt.issuer.issuance.IssuerOnboardingResponse
import id.walt.issuer.issuance.OnboardingRequest
import id.walt.issuer.services.onboarding.models.*
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import kotlinx.serialization.json.*
import kotlin.time.ExperimentalTime

object OnboardingService {

    private val iacaCertificateBuilder = IACACertificateBuilder()
    private val dsCertificateBuilder = DocumentSignerCertificateBuilder()

    suspend fun onboardIACA(
        request: IACAOnboardingRequest,
    ): IACAOnboardingResponse {

        val iacaKey = KeyManager.createKey(
            generationRequest = request.ecKeyGenRequestParams.toKeyGenerationRequest(),
        )

        val certBundle = iacaCertificateBuilder.build(
            profileData = request.certificateData.toIACACertificateProfileData(),
            signingKey = iacaKey,
        )

        return IACAOnboardingResponse(
            iacaKey = serializeGeneratedPrivateKeyToJsonObject(
                backend = request.ecKeyGenRequestParams.backend,
                key = iacaKey,
            ),
            certificateData = IACACertificateData(
                country = certBundle.decodedCertificate.principalName.country,
                commonName = certBundle.decodedCertificate.principalName.commonName,
                notBefore = certBundle.decodedCertificate.validityPeriod.notBefore,
                notAfter = certBundle.decodedCertificate.validityPeriod.notAfter,
                issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(
                    email = certBundle.decodedCertificate.issuerAlternativeName.email,
                    uri = certBundle.decodedCertificate.issuerAlternativeName.uri,
                ),
                stateOrProvinceName = certBundle.decodedCertificate.principalName.stateOrProvinceName,
                organizationName = certBundle.decodedCertificate.principalName.organizationName,
                crlDistributionPointUri = certBundle.decodedCertificate.crlDistributionPointUri,
            ),
            certificatePEM = certBundle.certificateDer.toPEMEncodedString(),
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
