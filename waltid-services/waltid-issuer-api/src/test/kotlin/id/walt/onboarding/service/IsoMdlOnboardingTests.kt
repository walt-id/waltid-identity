@file:OptIn(ExperimentalTime::class)

package id.walt.onboarding.service

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer.services.onboarding.OnboardingService
import id.walt.issuer.services.onboarding.models.*
import id.walt.x509.CertificateDer
import id.walt.x509.iso.documentsigner.parser.DocumentSignerCertificateParser
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import id.walt.x509.iso.iaca.parser.IACACertificateParser
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import okio.ByteString.Companion.toByteString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class IsoMdlOnboardingTests {

    companion object {

        private const val KEY_GEN_BACKEND = "jwk"

        private val signingKey = runBlocking {
            assertNotNull(
                JWKKey.importJWK(
                    """
                {
                    "kty": "EC",
                    "d": "u-UvsghdzpSXv5HmG5ngvm4Dv8yyRYw9fKA6mdp1KWs",
                    "crv": "P-256",
                    "kid": "R_E_QZ-Ea6etoAdWfUHSjjexRYz447ffnnfIO9kxn_Y",
                    "x": "n_b1GmZTSEhioK3z8MGqcb7nxXqyjFaLR-OfKOnspwU",
                    "y": "nGRVvuHTtEAZ1HjgdLaLZnYxrkiRV_e4V2Wz0qVWa-M"
                }
            """.trimIndent()
                ).getOrNull()
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

        private val validIACACertData = IACACertificateData(
            country = "US",
            commonName = "Example IACA",
            notBefore = Instant.parse("2025-05-28T12:23:01Z"),
            notAfter = Instant.parse("2040-05-24T12:23:01Z"),
            issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://ca.example.com"),
            crlDistributionPointUri = "https://ca.example.com/crl",
        )

        private val validIACASigner = IACASignerData(
            certificateData = validIACACertData,
            iacaKey = runBlocking {
                KeySerialization.serializeKeyToJson(signingKey).jsonObject
            },
        )

        private val iacaOnboardingRequest = IACAOnboardingRequest(
            certificateData = validIACACertReqData,
        )

        private val validDSReqData = DocumentSignerCertificateRequestData(
            country = "US",
            commonName = "Example DS",
            crlDistributionPointUri = "https://ca.example.com/crl",
        )

    }


    private fun pemToCertificateDer(pem: String) = CertificateDer(
        bytes = JWKKey.convertDERorPEMtoByteArray(
            derOrPem = pem,
        ).toByteString(),
    )

    @Test
    fun `onboard IACA root generates valid certificate`() = runTest {

        val response = OnboardingService.onboardIACA(iacaOnboardingRequest)

        val iacaDecodedCertificate = IACACertificateParser().parse(
            certificate = pemToCertificateDer(response.certificatePEM),
        )

        assertDoesNotThrow {
            IACAValidator().validate(
                decodedCert = iacaDecodedCertificate,
            )
        }

    }

    @Test
    fun `onboard Document Signer generates valid certificate`() = runTest {

        val now = Clock.System.now()

        val iacaResponse = OnboardingService.onboardIACA(iacaOnboardingRequest)

        val iacaDecodedCert = IACACertificateParser().parse(
            certificate = pemToCertificateDer(iacaResponse.certificatePEM),
        )

        val dsRequest = DocumentSignerOnboardingRequest(
            iacaSigner = IACASignerData(
                certificateData = validIACACertData,
                iacaKey = iacaResponse.iacaKey
            ),
            certificateData = DocumentSignerCertificateRequestData(
                country = "US",
                commonName = "Example DS",
                crlDistributionPointUri = "https://ca.example.com/crl",
                notBefore = now.plus(1.days),
            )
        )


        val response = OnboardingService.onboardDocumentSigner(dsRequest)

        val dsDecodedCert = DocumentSignerCertificateParser().parse(
            certificate = pemToCertificateDer(response.certificatePEM),
        )

        assertDoesNotThrow {
            DocumentSignerValidator().validate(
                dsDecodedCert = dsDecodedCert,
                iacaDecodedCert = iacaDecodedCert,
            )
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
        val iacaCertificateParser = IACACertificateParser()
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

            val iacaDecodedCertificate = iacaCertificateParser.parse(
                certificate = pemToCertificateDer(response.certificatePEM),
            )

            assertEquals(
                expected = request.ecKeyGenRequestParams.keyType,
                actual = iacaDecodedCertificate.publicKey.keyType,
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

        val dsCertificateParser = DocumentSignerCertificateParser()
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

            val dsDecodedCertificate = dsCertificateParser.parse(
                certificate = pemToCertificateDer(response.certificatePEM),
            )

            assertEquals(
                expected = request.ecKeyGenRequestParams.keyType,
                actual = dsDecodedCertificate.publicKey.keyType,
            )

        }

    }
}
