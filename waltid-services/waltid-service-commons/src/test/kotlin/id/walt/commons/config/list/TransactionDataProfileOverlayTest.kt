package id.walt.commons.config.list

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
