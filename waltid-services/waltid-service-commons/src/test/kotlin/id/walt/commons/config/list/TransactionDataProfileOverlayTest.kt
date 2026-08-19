package id.walt.commons.config.list

import id.walt.commons.web.ConflictException
import id.walt.commons.web.WebException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionDataProfileOverlayTest {

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

    @Test
    fun applyToKeepsSeedOrderAndAppendsOverrides() {
        val overlay = TransactionDataProfileOverlay(
            overrides = mapOf(
                paymentAuthorization.type to paymentAuthorization.copy(displayName = "Payment"),
                paymentCard.type to paymentCard,
            ),
        )
        assertEquals(
            listOf("SCA Payment", "Payment", "Payment Card"),
            overlay.applyTo(listOf(scaPayment, paymentAuthorization)).map { it.displayName },
        )
    }

    @Test
    fun rejectsOverlappingOverridesAndTombstones() {
        assertFailsWith<IllegalArgumentException> {
            TransactionDataProfileOverlay(
                overrides = mapOf(paymentCard.type to paymentCard),
                tombstones = setOf(paymentCard.type),
            )
        }
    }

    @Test
    fun createPreservesSeedOrderAndAppendsRuntimeProfiles() {
        val (overlay, created) = TransactionDataProfileOverlay().create(
            listOf(scaPayment, paymentAuthorization),
            paymentCard,
        )
        assertEquals(paymentCard, created)
        assertEquals(
            listOf(scaPayment.type, paymentAuthorization.type, paymentCard.type),
            overlay.applyTo(listOf(scaPayment, paymentAuthorization)).map { it.type },
        )
    }

    @Test
    fun createRejectsDuplicates() {
        assertFailsWith<ConflictException> {
            TransactionDataProfileOverlay().create(listOf(paymentAuthorization), paymentAuthorization)
        }
    }

    @Test
    fun deleteTombsASeededTypeUntilRecreated() {
        val seed = listOf(paymentAuthorization, scaPayment)
        val hidden = TransactionDataProfileOverlay().delete(seed, scaPayment.type)
        assertFalse(hidden.applyTo(seed).any { it.type == scaPayment.type })
        assertFailsWith<WebException> { hidden.requireExisting(seed, scaPayment.type) }

        val (restored, created) = hidden.create(seed, scaPayment.copy(displayName = "SCA Payment restored"))
        assertEquals("SCA Payment restored", created.displayName)
        assertEquals("SCA Payment restored", restored.requireExisting(seed, scaPayment.type).displayName)
    }

    @Test
    fun replaceUpdatesFieldsAndRejectsTypeMismatch() {
        val seed = listOf(paymentAuthorization)
        val (overlay, updated) = TransactionDataProfileOverlay().replace(
            seed,
            paymentAuthorization.type,
            paymentAuthorization.copy(displayName = "Payment"),
        )
        assertEquals("Payment", updated.displayName)
        assertEquals("Payment", overlay.requireExisting(seed, paymentAuthorization.type).displayName)

        assertFailsWith<IllegalArgumentException> {
            overlay.replace(seed, paymentAuthorization.type, paymentCard)
        }
    }

    @Test
    fun getUnknownTypeIsNotFound() {
        val error = assertFailsWith<WebException> {
            TransactionDataProfileOverlay().requireExisting(listOf(paymentAuthorization), "missing")
        }
        assertEquals(404, error.status)
        assertTrue(error.message.orEmpty().contains("missing"))
    }
}
