package id.walt.itb

import kotlin.test.*

class ItbRunSelectionTest {
    private val catalogue = ItbCatalogue.initialWalletCases()

    @Test
    fun defaultSelectionIncludesEveryDeployedInitialCase() {
        assertEquals("https://dev-i4mlab.aegean.gr", catalogue.origin.toString().trimEnd('/'))
        val selected = ItbRunSelection.select(catalogue, null)
        assertEquals(mapOf("cs01v1" to 7, "cs02v1" to 4, "cts07" to 1, "ts12" to 9),
            selected.groupingBy { it.first.id }.eachCount())
        assertEquals(21, selected.map { it.second.id }.distinct().size)
    }

    @Test
    fun filteredPresentationsIncludeTheirRealIssuancePrerequisitesFirst() {
        val selected = ItbRunSelection.select(catalogue, "ts12_pay_dc_api_03,tc_vp_007,tc15")
        assertEquals(listOf("tc_vci_006", "tc_vci_008", "tc_vp_007", "tc15", "ts12_issue_03", "ts12_pay_dc_api_03"),
            selected.map { it.second.id })
    }

    @Test
    fun unknownAndEmptySelectionsAreErrors() {
        listOf("", "tc_vp_004", "tc_vci_006,").forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { ItbRunSelection.select(catalogue, invalid) }
        }
    }
}
