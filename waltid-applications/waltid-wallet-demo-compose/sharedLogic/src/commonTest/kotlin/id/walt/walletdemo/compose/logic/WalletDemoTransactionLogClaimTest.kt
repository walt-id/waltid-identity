package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class WalletDemoTransactionLogClaimTest {
    @Test
    fun claimLabelsReuseTheCredentialVocabularyAndPreserveExactPaths() {
        val paths = mapOf(
            "given_name" to "Given name",
            "$.credentialSubject.family_name" to "Family name",
            """["org.iso.18013.5.1","birth_date"]""" to "Date of birth",
            "$['address']['postal_code']" to "Postal code",
            "custom_attribute" to "Custom attribute",
            "$" to "$",
        )
        paths.forEach { (path, label) ->
            val claim = WalletDemoTransactionLogClaim(path)
            assertEquals(label, claim.label, path)
            assertEquals(path, claim.path)
        }
    }
}
