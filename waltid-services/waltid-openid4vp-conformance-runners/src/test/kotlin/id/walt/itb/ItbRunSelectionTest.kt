package id.walt.itb

import kotlin.test.*

class ItbRunSelectionTest {
    private val catalogue = ItbCatalogue.walletCases()

    @Test
    fun defaultSelectionIncludesEverySelectedCase() {
        assertEquals("https://dev-i4mlab.aegean.gr", catalogue.origin.toString().trimEnd('/'))
        val selected = ItbRunSelection.select(catalogue, null)
        assertEquals(mapOf("cs01v1" to 7, "cs02v1" to 7, "cts07" to 1, "ts12" to 9),
            selected.groupingBy { it.first.id }.eachCount())
        assertEquals(24, selected.size)
    }

    @Test
    fun unattendedSelectionIncludesOnlyCasesWithoutUserAuthentication() {
        val selected = ItbRunSelection.select(catalogue, null, unattendedOnly = true)
        assertEquals(mapOf("cs01v1" to 7, "cs02v1" to 7, "cts07" to 1, "ts12" to 3),
            selected.groupingBy { it.first.id }.eachCount())
        assertTrue(selected.none { it.second.id.startsWith("ts12_pay_") })
    }

    @Test
    fun unattendedSelectionRejectsOperatorCasesEvenWhenExplicitlyRequested() {
        listOf("ts12_pay_01", "ts12_pay_dc_api_03", "tc_vp_007,ts12_pay_02").forEach { selection ->
            assertFailsWith<IllegalArgumentException> {
                ItbRunSelection.select(catalogue, selection, unattendedOnly = true)
            }
        }
        assertEquals(listOf("tc_vci_008", "tc_vp_007"),
            ItbRunSelection.select(catalogue, "tc_vp_007", unattendedOnly = true).map { it.second.id })
    }

    @Test
    fun filteredPresentationsIncludeTheirRealIssuancePrerequisitesFirst() {
        val selected = ItbRunSelection.select(catalogue, "ts12/ts12_pay_dc_api_03,cs01v1/tc_vci_005,cs02v1/tc_vp_007,tc15")
        assertEquals(listOf("tc_vci_006", "tc15", "tc_vci_008", "tc_vp_007", "tc_vci_005", "ts12_issue_03", "ts12_pay_dc_api_03"),
            selected.map { it.second.id })
    }

    @Test
    fun duplicateCaseIdsRequireQualificationAndDoNotSharePrerequisitesOrCiEligibility() {
        val presentation = catalogue.suites.single { it.id == "cs02v1" }
        val parallelSuite = presentation.copy(id = "another-suite", cases = presentation.cases.map {
            it.copy(prerequisite = null, attended = true)
        })
        val inventory = catalogue.copy(suites = catalogue.suites + parallelSuite)

        assertFailsWith<IllegalArgumentException> { ItbRunSelection.select(inventory, "tc_vp_007") }
        val selected = ItbRunSelection.select(inventory, "cs02v1/tc_vp_007", unattendedOnly = true)
        assertEquals(listOf("cs01v1" to "tc_vci_008", "cs02v1" to "tc_vp_007"),
            selected.map { (suite, case) -> suite.id to case.id })
        assertEquals(listOf(parallelSuite),
            ItbRunSelection.select(inventory, "another-suite/tc_vp_007").map { it.first })
        assertFailsWith<IllegalArgumentException> {
            ItbRunSelection.select(inventory, "another-suite/tc_vp_007", unattendedOnly = true)
        }
    }

    @Test
    fun invocationCasesRequireThePidAndTheirAdvertisedScheme() {
        val selected = ItbRunSelection.select(catalogue, "tc_vp_011,tc_vp_012,tc_vp_013", unattendedOnly = true)
        assertEquals(listOf("tc_vci_006", "tc_vp_011", "tc_vp_012", "tc_vp_013"), selected.map { it.second.id })
        assertEquals(listOf(null, "openid4vp", "eu-eaap", "haip-vp"), selected.map { it.second.invocationScheme })
        assertFailsWith<IllegalArgumentException> { ItbCatalogue.Case("test", "Test", "https") }
    }

    @Test
    fun dcIssuanceRequiresTheDemoBrowserPath() {
        assertFailsWith<IllegalArgumentException> { ItbRunSelection.select(catalogue, "cts07/tc16") }
        assertFailsWith<IllegalArgumentException> { ItbRunSelection.select(catalogue, "rv_iss_02") }
        val unattended = ItbRunSelection.select(catalogue, null, unattendedOnly = true, demoApp = true)
        assertEquals(21, unattended.size)
        // The OS picker fixes the credential set before the app opens. Register only the expected PID for tc15.
        assertEquals(listOf("tc_vci_006", "tc15", "tc16"), unattended.take(3).map { it.second.id })
    }

    @Test
    fun referenceCasesRequireAnExplicitSelectionAndTheDemoApp() {
        val references = listOf("onespan_com/onespan_vci_01", "onespan_com/onespan_vci_03", "rec/recweb_issue_01")
        fun caseKeys(selection: String?) = ItbRunSelection.select(catalogue, selection, unattendedOnly = true, demoApp = true)
            .map { (suite, case) -> "${suite.id}/${case.id}" }

        assertTrue(caseKeys(null).none { it in references })
        assertEquals(references, caseKeys(references.joinToString(",")))
        assertFailsWith<IllegalArgumentException> { ItbRunSelection.select(catalogue, "rec/recweb_issue_01") }
    }

    @Test
    fun unknownAndEmptySelectionsAreErrors() {
        listOf("", "tc_vp_004", "tc_vci_006,", "rec/recweb_issue_02").forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { ItbRunSelection.select(catalogue, invalid) }
        }
    }
}
