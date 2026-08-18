package id.walt.commons.config.list

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.ConflictException
import id.walt.commons.web.WebException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionDataProfileServiceTest {

    @AfterTest
    fun reset() {
        ConfigManager.preclear()
    }

    @Test
    fun listsSeedProfilesWhenOverlayIsEmpty() {
        preloadSeed(paymentAuthorization, scaPayment)
        assertEquals(
            setOf(paymentAuthorization.type, scaPayment.type),
            TransactionDataProfileService.list().map { it.type }.toSet(),
        )
        TransactionDataProfileService.toTypeRegistry().requireKnown(paymentAuthorization.type)
        TransactionDataProfileService.toTypeRegistry().requireKnown(scaPayment.type)
    }

    @Test
    fun createAddsRuntimeProfileAndRejectsDuplicates() {
        preloadSeed(paymentAuthorization)
        val created = TransactionDataProfileService.create(paymentCard)
        assertEquals(paymentCard, created)
        assertTrue(TransactionDataProfileService.list().any { it.type == paymentCard.type })

        assertFailsWith<ConflictException> {
            TransactionDataProfileService.create(paymentCard)
        }
    }

    @Test
    fun deleteTombsASeededTypeUntilRecreated() {
        preloadSeed(paymentAuthorization, scaPayment)
        TransactionDataProfileService.delete(scaPayment.type)
        assertFalse(TransactionDataProfileService.list().any { it.type == scaPayment.type })
        assertFailsWith<WebException> { TransactionDataProfileService.get(scaPayment.type) }

        TransactionDataProfileService.create(scaPayment.copy(displayName = "SCA Payment restored"))
        assertEquals("SCA Payment restored", TransactionDataProfileService.get(scaPayment.type).displayName)
    }

    @Test
    fun replaceUpdatesFieldsAndRejectsTypeMismatch() {
        preloadSeed(paymentAuthorization)
        val updated = TransactionDataProfileService.replace(
            paymentAuthorization.type,
            paymentAuthorization.copy(displayName = "Payment"),
        )
        assertEquals("Payment", updated.displayName)
        assertEquals("Payment", TransactionDataProfileService.get(paymentAuthorization.type).displayName)

        assertFailsWith<IllegalArgumentException> {
            TransactionDataProfileService.replace(paymentAuthorization.type, paymentCard)
        }
    }

    @Test
    fun getUnknownTypeIsNotFound() {
        preloadSeed(paymentAuthorization)
        val error = assertFailsWith<WebException> { TransactionDataProfileService.get("missing") }
        assertEquals(404, error.status)
    }

    private fun preloadSeed(vararg profiles: TransactionDataProfile) {
        ConfigManager.preloadAndRegisterConfig(
            "transaction-data-profiles",
            TransactionDataProfilesConfig(transactionDataProfiles = profiles.toList()),
        )
        ConfigManager.loadConfigs(emptyArray())
    }

    private val paymentAuthorization = TransactionDataProfile(
        type = "org.waltid.transaction-data.payment-authorization",
        displayName = "Payment Authorization",
        fields = listOf("merchant_name", "amount", "currency"),
    )
    private val scaPayment = TransactionDataProfile(
        type = "urn:eudi:sca:payment:1",
        displayName = "SCA Payment",
        fields = listOf("payload"),
    )
    private val paymentCard = TransactionDataProfile(
        type = "payment_card",
        displayName = "Payment Card",
        fields = listOf("merchant_name", "amount"),
    )
}
