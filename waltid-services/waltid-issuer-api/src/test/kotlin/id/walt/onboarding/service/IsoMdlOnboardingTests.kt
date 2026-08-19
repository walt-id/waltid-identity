package id.walt.onboarding.service

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import id.walt.crypto.keys.*
import id.walt.issuer.services.onboarding.OnboardingService
import id.walt.issuer.services.onboarding.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class IsoMdlOnboardingTests {

    companion object {

        private val rootCaUtil = X509CertificateUtil {
            addValidators(X509CertificateBasicConstraintsValidator(leafCanBeCa = true))
            addValidators(IsoIaCaRootX509CertificateProfile)
        }

        private val documentSignerUtil = X509CertificateUtil {
            addValidators(
                IsoDocumentSignerX509CertificateProfile,
                X509CertificateValidityValidator(allowValidityInFuture = true)
            )
        }

        private const val KEY_GEN_BACKEND = "jwk"

        private val rootCaKey: Key by lazy {
            runBlocking {
                KeyManager.createKey(
                    KeyGenerationRequest(
                        keyType = KeyType.secp256r1,
                    )
                )
            }
        }

        private val rootCa: X509Certificate by lazy {
            runBlocking {
                rootCaUtil.createSelfSignedCertificate(rootCaKey) {
                    profileIaCaRootCertificate(
                        issuerDnCountryCode = validIACACertReqData.country,
                        issuerDnStateOrProvinceName = validIACACertReqData.stateOrProvinceName,
                        issuerDnOrganizationName = validIACACertReqData.organizationName,
                        issuerDnCommonName = validIACACertReqData.commonName,
                        issuerEmailAddress = validIACACertReqData.issuerAlternativeNameConf.email,
                        issuerUri = validIACACertReqData.issuerAlternativeNameConf.uri
                    )
                }
            }
        }

        private val validIACASigner: IACASignerData by lazy {
            IACASignerData(
                iacaKey = KeySerialization.serializeKeyToJson(rootCaKey).jsonObject,
                iacaPem = rootCa.encodedPem
            )
        }


        private val validIACACertReqData = IACACertificateRequestData(
            country = "US",
            commonName = "Example IACA",
            notBefore = Instant.parse("2025-05-28T12:23:01Z"),
            notAfter = Instant.parse("2040-05-24T12:23:01Z"),
            issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://ca.example.com"),
            crlDistributionPointUri = "https://ca.example.com/crl",
        )

        private val iacaOnboardingRequest = IACAOnboardingRequest(
            certificateData = validIACACertReqData,
        )

        private val validDSReqData = DocumentSignerCertificateRequestData(
            country = "US",
            commonName = "Example DS",
            crlDistributionPointUri = "https://ca.example.com/crl",
            issuerEmailAddress = "office@walt.id"
        )
    }

    @Test
    fun `onboard IACA root generates valid certificate`() = runTest {
        val response = OnboardingService.onboardIACA(iacaOnboardingRequest)
        val iaCaRootCert = X509CertificateUtil.parseCertificatePem(response.certificatePEM)
        val validationResult = rootCaUtil.validateCertificateChain(listOf(iaCaRootCert), iaCaRootCert)
        assertTrue(validationResult.valid)
        assertTrue(validationResult.log.any { it.validatorId == IsoIaCaRootX509CertificateProfile.ID })
    }

    @Test
    fun `onboard Document Signer generates valid certificate`() = runTest {
        val now = Clock.System.now()
        val iacaResponse = OnboardingService.onboardIACA(iacaOnboardingRequest)
        val iacaDecodedCert = rootCaUtil.parseCertificatePem(iacaResponse.certificatePEM)

        val dsRequest = DocumentSignerOnboardingRequest(
            iacaSigner = IACASignerData(
                iacaKey = iacaResponse.iacaKey,
                iacaPem = iacaResponse.certificatePEM,
            ),
            certificateData = DocumentSignerCertificateRequestData(
                country = "US",
                commonName = "Example DS",
                crlDistributionPointUri = "https://ca.example.com/crl",
                notBefore = now.plus(1.days),
                issuerEmailAddress = "office@walt.id"
            )
        )

        val response = OnboardingService.onboardDocumentSigner(dsRequest)
        val dsDecodedCert = documentSignerUtil.parseCertificatePem(response.certificatePEM)
        val validationResult = documentSignerUtil.validateCertificateChain(listOf(dsDecodedCert), iacaDecodedCert)

        if (!validationResult.valid) {
            validationResult.log
                .filter { it.severity == ValidationResult.Severity.ERROR }
                .forEach { println(it) }
        }
        assertTrue(validationResult.valid)
        assertTrue(validationResult.log.any { it.validatorId == IsoDocumentSignerX509CertificateProfile.ID })
    }

    @Test
    fun `onboard Document Signer rejects a non-IACA-profile-compliant root certificate`() = runTest {
        // A self-signed cert built without the IACA profile helper: e.g. it won't have the
        // mandatory issuer-alt-name extension, a critical basicConstraints with pathLenConstraint=0,
        // or a critical keyUsage restricted to keyCertSign+cRLSign. A caller controlling the private
        // key behind such a certificate must not be able to get a Document Signer issued "under" it.
        val notARealRootKey = KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1))
        val now = Clock.System.now()
        val notARealRootCert = X509CertificateUtil.createSelfSignedCertificate(notARealRootKey) {
            subjectDn = "CN=Not A Real IACA Root"
            // wide enough to comfortably cover validDSReqData's default validity window, so the
            // only thing that can reject this request is the (missing) IACA profile compliance
            validity = X509Certificate.Validity(
                notBefore = now.minus(1.days),
                notAfter = now.plus(500.days),
            )
        }

        val request = DocumentSignerOnboardingRequest(
            iacaSigner = IACASignerData(
                iacaKey = KeySerialization.serializeKeyToJson(notARealRootKey).jsonObject,
                iacaPem = notARealRootCert.encodedPem,
            ),
            certificateData = validDSReqData,
        )

        assertFails {
            OnboardingService.onboardDocumentSigner(request)
        }
    }

    @Test
    fun `onboard IACA does not work with unsupported key types`() = runTest {
        listOf(
            IACAOnboardingRequest(
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.Ed25519,
                )
            ),
            IACAOnboardingRequest(
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.secp256k1,
                )
            ),
            IACAOnboardingRequest(
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.RSA,
                )
            ),
            IACAOnboardingRequest(
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.RSA3072,
                )
            ),
            IACAOnboardingRequest(
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.RSA4096,
                )
            ),
        ).forEach { request ->
            assertFails {
                OnboardingService.onboardIACA(request)
            }
        }
    }

    @Test
    fun `onboard IACA works with all supported key types`() = runTest {
        listOf(
            IACAOnboardingRequest( //ensure by default a valid key is generated
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                )
            ),
            IACAOnboardingRequest(
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.secp256r1,
                )
            ),
            IACAOnboardingRequest(
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.secp384r1,
                )
            ),
            IACAOnboardingRequest(
                certificateData = validIACACertReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.secp521r1,
                )
            ),
        ).forEach { request ->
            val response = assertDoesNotThrow {
                OnboardingService.onboardIACA(request)
            }

            val generatedKey = assertDoesNotThrow {
                KeyManager.resolveSerializedKey(response.iacaKey.toString())
            }

            assertEquals(
                expected = request.ecKeyGenRequestParams.keyType,
                actual = generatedKey.keyType,
            )
            val iacaDecodedCertificate = rootCaUtil.parseCertificatePem(response.certificatePEM)
            val validationResult =
                rootCaUtil.validateCertificateChain(listOf(iacaDecodedCertificate), iacaDecodedCertificate)
            assertTrue(validationResult.valid)
            assertTrue(validationResult.log.any { it.validatorId == IsoIaCaRootX509CertificateProfile.ID })

            val publicKeyInfo = iacaDecodedCertificate.data.subjectPublicKeyInfo
            val requestedPublicKeyInfo = X509SigningAlgorithmInfo.ofKeyType(request.ecKeyGenRequestParams.keyType)

            assertEquals(
                expected = requestedPublicKeyInfo.keyAlgorithmOid,
                actual = publicKeyInfo.algorithmOid,
            )
        }
    }

    @Test
    fun `onboard Document Signer does not work with unsupported key types`() = runTest {

        listOf(
            DocumentSignerOnboardingRequest(
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.secp256k1,
                ),
            ),
            DocumentSignerOnboardingRequest(
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.Ed25519,
                ),
            ),
            DocumentSignerOnboardingRequest(
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.RSA,
                ),
            ),
            DocumentSignerOnboardingRequest(
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.RSA3072,
                ),
            ),
            DocumentSignerOnboardingRequest(
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.RSA4096,
                ),
            ),
        ).forEach { request ->

            assertFails {
                OnboardingService.onboardDocumentSigner(request)
            }

        }
    }

    @Test
    fun `onboard Document Signer works with all supported key types`() = runTest {
        listOf(
            DocumentSignerOnboardingRequest(
                //ensure by default a valid key is generated
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                ),
            ),
            DocumentSignerOnboardingRequest(
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.secp256r1,
                ),
            ),
            DocumentSignerOnboardingRequest(
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.secp384r1,
                ),
            ),
            DocumentSignerOnboardingRequest(
                iacaSigner = validIACASigner,
                certificateData = validDSReqData,
                ecKeyGenRequestParams = KeyGenerationRequestParameters(
                    backend = KEY_GEN_BACKEND,
                    keyType = KeyType.secp521r1,
                ),
            ),
        ).forEach { request ->

            val response = assertDoesNotThrow {
                OnboardingService.onboardDocumentSigner(request)
            }

            val generatedKey = assertDoesNotThrow {
                KeyManager.resolveSerializedKey(response.documentSignerKey.toString())
            }

            assertEquals(
                expected = request.ecKeyGenRequestParams.keyType,
                actual = generatedKey.keyType,
            )

            val documentSignerCert = X509CertificateUtil.parseCertificatePem(response.certificatePEM)
            val publicKeyInfo = documentSignerCert.data.subjectPublicKeyInfo
            val requestedPublicKeyInfo = X509SigningAlgorithmInfo.ofKeyType(request.ecKeyGenRequestParams.keyType)

            assertEquals(
                expected = requestedPublicKeyInfo.keyAlgorithmOid,
                actual = publicKeyInfo.algorithmOid,
            )
        }
    }
}
