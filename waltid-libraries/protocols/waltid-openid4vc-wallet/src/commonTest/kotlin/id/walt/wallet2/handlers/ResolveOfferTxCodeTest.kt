package id.walt.wallet2.handlers

import id.walt.openid4vci.offers.TxCode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveOfferTxCodeTest {
    @Test
    fun preservesUserInputMetadata() {
        val result = with(WalletIssuanceHandler) {
            TxCode(
                inputMode = "text",
                length = 8,
                description = "Enter the code from your letter",
            ).toUserInputRequirement()
        }

        assertEquals(ResolveOfferTxCodeInputMode.text, result?.inputMode)
        assertEquals(8, result?.length)
        assertEquals("Enter the code from your letter", result?.description)
    }

    @Test
    fun defaultsMissingInputModeToNumeric() {
        val result = with(WalletIssuanceHandler) {
            TxCode(length = 6).toUserInputRequirement()
        }

        assertEquals(ResolveOfferTxCodeInputMode.numeric, result?.inputMode)
    }

    @Test
    fun omitsRequirementWhenOfferContainsTransactionCodeValue() {
        val result = with(WalletIssuanceHandler) {
            TxCode(value = JsonPrimitive("123456")).toUserInputRequirement()
        }

        assertNull(result)
    }
}
