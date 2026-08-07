package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.runner.IssuerModuleSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IssuerModuleSelectionTest {

    @Test
    fun explicitExclusionOverridesSelectedPositiveGroup() {
        val module = "oid4vci-1_0-issuer-happy-flow-additional-requests"
        val selection = IssuerModuleSelection(
            groups = setOf("positive"),
            excludedModules = setOf(module),
        )

        assertTrue(selection.matches(module))
        assertNotNull(selection.exclusionReason(module))
        assertEquals(null, selection.exclusionReason("oid4vci-1_0-issuer-happy-flow"))
    }
}
