package id.walt.wallet2.mobile

import id.walt.openid4vci.offers.TxCode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MobileWalletOfferResolverTest {
    @Test
    fun preservesUserInputMetadata() {
        val result = TxCode(
            inputMode = "text",
            length = 8,
            description = "Enter the code from your letter",
        ).toMobileWalletTxCode()

        assertEquals(MobileWalletTxCodeInputMode.text, result?.inputMode)
        assertEquals(8, result?.length)
        assertEquals("Enter the code from your letter", result?.issuerDescription)
    }

    @Test
    fun defaultsMissingInputModeToNumeric() {
        val result = TxCode(length = 6).toMobileWalletTxCode()

        assertEquals(MobileWalletTxCodeInputMode.numeric, result?.inputMode)
    }

    @Test
    fun omitsRequirementWhenOfferContainsTransactionCodeValue() {
        val result = TxCode(value = JsonPrimitive("123456")).toMobileWalletTxCode()

        assertNull(result)
    }

    @Test
    fun rejectsNonPositiveLength() {
        listOf(-1, 0).forEach { length ->
            assertFailsWith<IllegalArgumentException> {
                TxCode(length = length).toMobileWalletTxCode()
            }
        }
    }
}
