package id.walt.walletdemo.compose.logic

import kotlin.test.*
import kotlin.time.Instant

class WalletDemoPaymentDisplayTest {
    @Test fun paymentTimestampsRemainTypedAndRawValuesSurviveTheDisplayBoundary() {
        val raw = "2026-10-25T02:30:00.123456789+02:00"
        val paths = listOf(listOf("date_time"), listOf("execution_date"),
            listOf("recurrence", "start_date"), listOf("recurrence", "end_date"))
        val fields = paths.map { WalletDemoPaymentField("Issuer label", "Description", raw,
            WalletDemoPaymentFieldPlacement.Main, it) }
        val display = display(fields)
        for ((index, field) in display.fields.withIndex()) {
            assertEquals(DisplayValue.InstantValue(Instant.parse("2026-10-25T00:30:00.123456789Z")), field.item.value)
            assertEquals(paths[index], field.item.pathComponents)
            assertEquals(raw, field.item.rawValue)
            assertEquals("Issuer label", field.item.label)
            assertEquals("Description", field.description)
            assertEquals(WalletDemoPaymentFieldPlacement.Main, field.placement)
        }
        assertTrue(fields.all { it.value == raw })
    }

    @Test fun labelsUnknownPathsAndUnsupportedTimestampRepresentationsCannotInventDates() {
        val date = "2026-10-25T00:30:00Z"
        val fields = listOf(
            WalletDemoPaymentField("Execution date", null, date, WalletDemoPaymentFieldPlacement.Main),
            WalletDemoPaymentField("Issued at", null, date, WalletDemoPaymentFieldPlacement.Main, listOf("transaction_id")),
        ) + listOf("2026-10-25", "2026-10-25T00:30:00", "2026-10-25T00:30:00.1234567891Z", "invalid").map {
            WalletDemoPaymentField("Issuer label", null, it, WalletDemoPaymentFieldPlacement.Details, listOf("execution_date"))
        }
        assertEquals(fields.map { DisplayValue.Text(it.value) }, display(fields).fields.map { it.item.value })
        assertEquals(fields.map { it.value }, display(fields).fields.map { it.item.rawValue })
    }

    @Test fun paymentScheduleAndMonetaryTermsHaveSeparateGroupsWithoutChangingIssuerInstructions() {
        val schedule = listOf(listOf("execution_date"), listOf("recurrence", "start_date"),
            listOf("recurrence", "end_date"), listOf("recurrence", "number"), listOf("recurrence", "frequency"))
        val terms = listOf("amount_variable", "min_amount", "max_amount", "total_amount", "initial_amount", "initial_amount_number", "apr")
            .map { listOf("recurrence", "mit_options", it) }
        val paths = schedule + terms + listOf(listOf("transaction_id"), listOf("recurrence", "unknown"))
        for (placement in WalletDemoPaymentFieldPlacement.entries) {
            val fields = paths.map { WalletDemoPaymentField("Issuer label", "Issuer description", "9007199254740993.01", placement, it) }
            val actual = display(fields).fields
            assertEquals(List(schedule.size) { PaymentDisplayGroup.Timing } + List(terms.size) { PaymentDisplayGroup.Terms } +
                List(2) { PaymentDisplayGroup.Other }, actual.map { it.group })
            assertEquals(paths, actual.map { it.paymentPath })
            assertTrue(actual.all { it.placement == placement && it.description == "Issuer description" &&
                it.item.label == "Issuer label" && it.item.rawValue == "9007199254740993.01" })
        }
    }

    private fun display(fields: List<WalletDemoPaymentField>) = WalletDemoPaymentReview.Ready(
        WalletDemoPaymentConsent("revision", "en", null, null, "Pay", null, false, fields)
    ).displays(emptyList()).single()
}
