package id.walt.walletdemo.compose.logic

import id.walt.wallet2.transactionlog.ClaimInfo
import id.walt.wallet2.transactionlog.Presentation
import id.walt.wallet2.transactionlog.PresentationTransactionLog
import id.walt.wallet2.transactionlog.TransactionResult
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletDemoTransactionLogMappingTest {
    @Test
    fun requestedAndPresentedClaimsStayScopedToTheirCredentialTypes() {
        val entry = presentation(
            requested = listOf(
                ClaimInfo("Identity", listOf("given_name", "family_name")),
                ClaimInfo("DrivingLicence", listOf("""["org.iso.18013.5.1","birth_date"]""")),
                ClaimInfo("Identity", listOf("given_name", "nationality")),
            ),
            presented = listOf(
                ClaimInfo("Identity", listOf("given_name")),
                ClaimInfo("AdditionalCredential", listOf("age_over_18")),
            ),
        ).toDemoEntry()

        assertEquals(listOf("Identity", "DrivingLicence", "AdditionalCredential"), entry.credentials.map { it.credentialIdentifier })
        val identity = entry.credentials[0]
        assertEquals(listOf("given_name", "family_name", "nationality"), identity.requestedClaims.map { it.path })
        assertEquals(listOf("Given name", "Family name", "Nationality"), identity.requestedClaims.map { it.label })
        assertEquals(listOf("given_name"), identity.presentedClaims.map { it.path })
        val licence = entry.credentials[1]
        assertEquals("Date of birth", licence.requestedClaims.single().label)
        assertEquals("""["org.iso.18013.5.1","birth_date"]""", licence.requestedClaims.single().path)
        assertTrue(licence.presentedClaims.isEmpty())
        assertTrue(entry.credentials[2].requestedClaims.isEmpty())
        assertEquals("Age over 18", entry.credentials[2].presentedClaims.single().label)
    }

    @Test
    fun unsuccessfulPresentationDoesNotTreatRequestedClaimsAsPresented() {
        val entry = presentation(
            requested = listOf(ClaimInfo("Identity", listOf("given_name", "family_name"))),
            reason = "User declined",
        ).toDemoEntry()

        assertEquals(WalletDemoTransactionLogResult.NotCompleted, entry.result)
        assertEquals(2, entry.credentials.single().requestedClaims.size)
        assertTrue(entry.credentials.single().presentedClaims.isEmpty())
        assertTrue(entry.details.any { it.label == "Reason not completed" && it.value == "User declined" })
    }

    @Test
    fun emptyPresentationHasNoInventedCredentialClaims() {
        assertTrue(presentation().toDemoEntry().credentials.isEmpty())
    }

    private fun presentation(
        requested: List<ClaimInfo> = emptyList(),
        presented: List<ClaimInfo> = emptyList(),
        reason: String? = null,
    ) = PresentationTransactionLog(
        transactionIdentifier = "presentation",
        time = LocalDateTime(2026, 10, 25, 16, 0),
        transactionResult = if (reason == null) TransactionResult.Completed else TransactionResult.NotCompleted,
        presentation = Presentation(
            listOfClaimsRequested = requested,
            listOfClaimsPresented = presented,
            reasonOfNoncompletion = reason,
        ),
    )
}
