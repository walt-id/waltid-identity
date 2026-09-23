package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WalletDemoKeySetupTest {
    private fun choice(id: String) = WalletDemoKeyChoice(id, id, "Description of $id")
    private fun option(recovery: String, storage: String, approval: String) = WalletDemoKeySetupOption(
        "$recovery-$storage-$approval", choice(recovery), choice(storage), choice(approval),
    )
    // iOS: recovery excludes hardware; database storage excludes system approval.
    private val options = listOf(
        option("new", "hardware", "biometric"), option("new", "native", "biometric"),
        option("new", "hardware", "none"), option("new", "native", "none"), option("new", "database", "none"),
        option("backup", "native", "biometric"), option("backup", "native", "none"), option("backup", "database", "none"),
        option("restore:one", "native", "none"), option("restore:two", "native", "none"),
    )

    @Test
    fun recoveryOptionsAreGroupedWithoutMergingDifferentRecords() {
        val step = WalletDemoKeySetupStep.Recovery
        assertEquals(listOf("new", "backup", "restore:one", "restore:two"),
            step.options(options, options.first()).map { step.choice(it).id }.distinct())
    }

    @Test
    fun choosingRecoveryRetainsApprovalAndOnlyOffersCompatibleStorage() {
        val selected = WalletDemoKeySetupStep.Recovery.select(options, options.first(), "backup")
        assertEquals("biometric", selected.approval.id)
        val storage = WalletDemoKeySetupStep.Storage.options(options, selected)
        assertEquals(setOf("native", "database"), storage.map { it.storage.id }.toSet())
        assertTrue(storage.all { it.recovery.id == "backup" })
    }

    @Test
    fun databaseSelectionOnlyOffersNoPromptAndAlwaysSubmitsAnOriginalHandle() {
        val step = WalletDemoKeySetupStep.Storage
        val selected = step.select(options, options.first(), "database")
        assertSame(options[4], selected)
        assertEquals(listOf("none"), WalletDemoKeySetupStep.Approval.options(options, selected).map { it.approval.id })
        assertSame(selected, WalletDemoKeySetupStep.Approval.select(options, selected, "biometric"))
    }

    @Test
    fun backNavigationPreservesCompatibleChoices() {
        val none = options[3]
        val recovery = WalletDemoKeySetupStep.Recovery.select(options, none, "backup")
        assertEquals("native", recovery.storage.id)
        assertEquals("none", recovery.approval.id)
        assertSame(none, WalletDemoKeySetupStep.Recovery.select(options, recovery, "new"))
    }
}
