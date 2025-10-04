@file:OptIn(ExperimentalTime::class)

package id.walt.onboarding.models

import id.walt.issuer.services.onboarding.models.IACACertificateData
import id.walt.issuer.services.onboarding.models.IssuerAlternativeNameConfiguration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class IACACertificateDataValidationTest {

    private val validIACACertData = IACACertificateData(
        country = "US",
        commonName = "Example IACA",
        issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://ca.example.com")
    )

    @Test
    fun `valid payload does not throw`() {
        assertNotNull(validIACACertData.finalNotBefore)
        assertNotNull(validIACACertData.finalNotAfter)
    }

    @Test
    fun `invalid country code throws`() {
        assertFailsWith<IllegalArgumentException> {
            validIACACertData.copy(country = "XX")
        }
    }

    @Test
    fun `blank stateOrProvinceName throws`() {
        assertFailsWith<IllegalArgumentException> {
            validIACACertData.copy(stateOrProvinceName = "   ")
        }
    }

    @Test
    fun `blank organizationName throws`() {
        assertFailsWith<IllegalArgumentException> {
            validIACACertData.copy(organizationName = "")
        }
    }

    @Test
    fun `notBefore in the past throws`() {
        val past = Clock.System.now().minus(1.minutes)
        assertFailsWith<IllegalArgumentException> {
            validIACACertData.copy(notBefore = past)
        }
    }

    @Test
    fun `notAfter before notBefore throws`() {
        val now = Clock.System.now()
        val after = now.minus(1.days)
        assertFailsWith<IllegalArgumentException> {
            validIACACertData.copy(notBefore = now, notAfter = after)
        }
    }

    @Test
    fun `notAfter equal to notBefore throws`() {
        val now = Clock.System.now()
        assertFailsWith<IllegalArgumentException> {
            validIACACertData.copy(notBefore = now, notAfter = now)
        }
    }
}
