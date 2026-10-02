package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletDemoOfferSelectionTest {
    private val holder = WalletDemoHolderBinding("existing", "did:key:existing")
    private val offer = WalletDemoOfferPreview(
        WalletDemoIssuerMetadata("https://issuer.example", null),
        listOf("pid", "mdl", "employee").map {
            WalletDemoOfferedCredentialMetadata(it, "mso_mdoc", null, null, null, emptyList())
        },
        transactionCode = null, batchSize = 3,
    )

    @Test fun explicitCountsKeepSingleExistingKeysAndRequestNewKeysOnlyForCopies() {
        assertEquals(listOf(
            WalletDemoCredentialSelection("pid", WalletDemoCredentialHolders.Existing(listOf(holder))),
            WalletDemoCredentialSelection("mdl", WalletDemoCredentialHolders.NewKeys(3)),
        ), offer.credentialSelections(mapOf("mdl" to 3, "employee" to 0), holder))
    }

    @Test fun emptyUnknownAndOutOfRangeSelectionsCannotReachTheIssuer() {
        listOf(
            mapOf("pid" to 0, "mdl" to 0, "employee" to 0),
            mapOf("unknown" to 1), mapOf("pid" to -1), mapOf("pid" to 4),
        ).forEach { copies -> assertFailsWith<IllegalArgumentException> { offer.credentialSelections(copies, holder) } }
        assertFailsWith<IllegalArgumentException> {
            offer.copy(batchSize = null).credentialSelections(mapOf("pid" to 2), holder)
        }
    }
}
