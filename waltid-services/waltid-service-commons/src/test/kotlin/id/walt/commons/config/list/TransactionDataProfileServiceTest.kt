package id.walt.commons.config.list

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.ConflictException
import id.walt.commons.web.WebException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
            listOf(paymentAuthorization.type, scaPayment.type),
            TransactionDataProfileService.list().map { it.type },
        )
        TransactionDataProfileService.toTypeRegistry().requireKnown(paymentAuthorization.type)
        TransactionDataProfileService.toTypeRegistry().requireKnown(scaPayment.type)
    }

    @Test
    fun createPreservesSeedOrderAndAppendsRuntimeProfiles() {
        preloadSeed(scaPayment, paymentAuthorization)
        TransactionDataProfileService.create(paymentCard)
        assertEquals(
            listOf(scaPayment.type, paymentAuthorization.type, paymentCard.type),
            TransactionDataProfileService.list().map { it.type },
        )
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

    @Test
    fun concurrentCreatesAreNotLostAndDuplicatesConflict() {
        preloadSeed(paymentAuthorization)
        val createdTypes = (1..40).map { "org.example.runtime-$it" }
        val createErrors = ConcurrentLinkedQueue<Throwable>()
        val createLatch = CountDownLatch(createdTypes.size)
        val pool = Executors.newFixedThreadPool(8)
        try {
            createdTypes.forEach { type ->
                pool.submit {
                    runCatching {
                        TransactionDataProfileService.create(
                            TransactionDataProfile(type = type, displayName = type, fields = listOf("amount")),
                        )
                    }.onFailure(createErrors::add)
                    createLatch.countDown()
                }
            }
            assertTrue(createLatch.await(10, TimeUnit.SECONDS))
            assertTrue(createErrors.isEmpty(), createErrors.joinToString())
            assertEquals(
                (listOf(paymentAuthorization.type) + createdTypes).toSet(),
                TransactionDataProfileService.list().map { it.type }.toSet(),
            )

            val outcomes = ConcurrentLinkedQueue<Result<TransactionDataProfile>>()
            val duplicateLatch = CountDownLatch(16)
            repeat(16) {
                pool.submit {
                    outcomes.add(runCatching { TransactionDataProfileService.create(paymentCard) })
                    duplicateLatch.countDown()
                }
            }
            assertTrue(duplicateLatch.await(10, TimeUnit.SECONDS))
            assertEquals(1, outcomes.count { it.isSuccess })
            assertTrue(outcomes.filter { it.isFailure }.all { it.exceptionOrNull() is ConflictException })
            assertEquals(1, TransactionDataProfileService.list().count { it.type == paymentCard.type })
        } finally {
            pool.shutdownNow()
        }
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
