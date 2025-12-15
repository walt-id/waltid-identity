@file:OptIn(ExperimentalTime::class)

package id.walt.onboarding.models

import id.walt.issuer.services.onboarding.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

class DocumentSignerOnboardingRequestTest {

    private val iacaKey = Json.decodeFromString<JsonElement>("""
        {
            "type": "jwk",
            "jwk": {
                "kty": "EC",
                "d": "GA1U5hViiQiQIaEu84Xq4DfAoL54HB_FGDDJtPp4rC0",
                "crv": "P-256",
                "kid": "MUdS-lzlC_4qDFq2XFb6PxpRitd_gglJrlYe1VOVsh0",
                "x": "dUpq6W-c6yLniF3k5Lj5j4rFysYXmnHTSK_aOb2JV_A",
                "y": "urKlUBnHoHIeBP9YuCj9ZwVBewS4yNkaT1J64gQLnEw"
            }
        }
    """.trimIndent())

    private val validDSCertData = DocumentSignerCertificateData(
            country = "US",
            commonName = "Example Document Signer",
            crlDistributionPointUri = "https://ca.example.com/crl"
        )

    private val validIACASignerData = IACASignerData(
        iacaKey = iacaKey,
        certificateData = IACACertificateData(
            country = "US",
            commonName = "Example IACA",
            issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://ca.example.com")
        )
    )

    @Test
    fun `valid onboarding request does not throw`() {
        val request = DocumentSignerOnboardingRequest(
            iacaSigner = validIACASignerData,
            certificateData = validDSCertData
        )
        assertNotNull(request)
    }

    @Test
    fun `different country between IACA and DS throws`() {
        val badIACA = validIACASignerData.copy(
            certificateData = validIACASignerData.certificateData.copy(country = "GR")
        )
        assertFailsWith<IllegalArgumentException> {
            DocumentSignerOnboardingRequest(
                iacaSigner = badIACA,
                certificateData = validDSCertData
            )
        }
    }

    @Test
    fun `different stateOrProvinceName between IACA and DS throws`() {
        val iaca = validIACASignerData.copy(
            certificateData = validIACASignerData.certificateData.copy(stateOrProvinceName = "Thessaloniki")
        )
        val dsc = validDSCertData.copy(stateOrProvinceName = "Chania")
        assertFailsWith<IllegalArgumentException> {
            DocumentSignerOnboardingRequest(
                iacaSigner = iaca,
                certificateData = dsc
            )
        }
    }

    @Test
    fun `invalid country code throws`() {
        assertFailsWith<IllegalArgumentException> {
            validDSCertData.copy(country = "XX")
        }
    }

    @Test
    fun `blank organizationName throws`() {
        assertFailsWith<IllegalArgumentException> {
            validDSCertData.copy(organizationName = " ")
        }
    }

    @Test
    fun `blank stateOrProvinceName throws`() {
        assertFailsWith<IllegalArgumentException> {
            validDSCertData.copy(stateOrProvinceName = "")
        }
    }

    @Test
    fun `notAfter before notBefore throws`() {
        val now = Clock.System.now()
        val before = now.minus(1.days)
        assertFailsWith<IllegalArgumentException> {
            validDSCertData.copy(notBefore = now, notAfter = before)
        }
    }

    @Test
    fun `notAfter equal to notBefore throws`() {
        val now = Clock.System.now()
        assertFailsWith<IllegalArgumentException> {
            validDSCertData.copy(notBefore = now, notAfter = now)
        }
    }

    @Test
    fun `document signer certificate validity larger than 457 days throws`() {
        val now = Clock.System.now()
        assertFailsWith<IllegalArgumentException> {
            validDSCertData.copy(
                notAfter = now.plus((458L).toDuration(DurationUnit.DAYS)),
            )
        }
    }
}
