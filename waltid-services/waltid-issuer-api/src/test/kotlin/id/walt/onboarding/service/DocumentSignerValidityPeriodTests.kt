@file:OptIn(ExperimentalTime::class)

package id.walt.onboarding.service

import id.walt.issuer.services.onboarding.OnboardingService
import id.walt.issuer.services.onboarding.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.*
import kotlin.time.Duration.Companion.days

class DocumentSignerValidityPeriodTest {

    private fun onboardTestIACA(
        validUntil: Instant,
    ) = runBlocking {
        val request = IACAOnboardingRequest(
            certificateData = IACACertificateRequestData(
                country = "US",
                commonName = "Test IACA",
                issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://iaca.example.com"),
                notAfter = validUntil,
                crlDistributionPointUri = "https://iaca.example.com/crl"
            )
        )
        OnboardingService.onboardIACA(request)
    }

    @Test
    fun `document signer validity period must be within IACA validity period`() = runTest {
        val timeNow = Clock.System.now()
        val iacaNotAfter = timeNow.plus((365L).toDuration(DurationUnit.DAYS))
        val iacaResponse = onboardTestIACA(iacaNotAfter)

        val iacaSigner = IACASignerData(
            certificateData = IACACertificateData(
                country = "US",
                commonName = "Test IACA",
                issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://iaca.example.com"),
                notBefore = iacaResponse.certificateData.notBefore,
                notAfter = iacaResponse.certificateData.notAfter,
                crlDistributionPointUri = "https://iaca.example.com/crl"
            ),
            iacaKey = iacaResponse.iacaKey,
        )

        // Valid: DS certificate is fully within IACA period
        OnboardingService.onboardDocumentSigner(
            DocumentSignerOnboardingRequest(
                iacaSigner = iacaSigner,
                certificateData = DocumentSignerCertificateRequestData(
                    country = "US",
                    commonName = "Valid DSC",
                    crlDistributionPointUri = "https://iaca.example.com/crl",
                    notBefore = timeNow.plus(30.days),
                    notAfter = timeNow.plus(364.days),
                )
            )
        )

        // Invalid: DS certificate starts before IACA
        assertFailsWith<IllegalArgumentException> {
            OnboardingService.onboardDocumentSigner(
                DocumentSignerOnboardingRequest(
                    iacaSigner = iacaSigner,
                    certificateData = DocumentSignerCertificateRequestData(
                        country = "US",
                        commonName = "DSC starting before IACA",
                        crlDistributionPointUri = "https://iaca.example.com/crl",
                        notBefore = timeNow.minus((1L).toDuration(DurationUnit.DAYS)),
                        notAfter = timeNow.plus((365L).toDuration(DurationUnit.DAYS))
                    )
                )
            )
        }

        // Invalid: DS certificate ends after IACA
        assertFailsWith<IllegalArgumentException> {
            OnboardingService.onboardDocumentSigner(
                DocumentSignerOnboardingRequest(
                    iacaSigner = iacaSigner,
                    certificateData = DocumentSignerCertificateRequestData(
                        country = "US",
                        commonName = "DSC ending after IACA",
                        crlDistributionPointUri = "https://iaca.example.com/crl",
                        notBefore = timeNow.plus((30L).toDuration(DurationUnit.DAYS)),
                        notAfter = timeNow.plus((366L).toDuration(DurationUnit.DAYS))
                    )
                )
            )
        }
    }
}
