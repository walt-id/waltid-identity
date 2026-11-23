package id.walt.credentials.status

import id.walt.mdoc.objects.mso.Status
import id.walt.policies2.vc.policies.status.entry.requireEvaluableStatusList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StatusReferenceExtractionTest {
    @Test
    fun emptyPresentMsoStatusIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> { Status().requireEvaluableStatusList("mdoc") }
        assertEquals(
            "mdoc has a status object with no status_list, identifier_list, or ETSI flat status members. " +
                "credential-status evaluates an IETF Token Status List (status.status_list.idx and status.status_list.uri).",
            error.message,
        )
    }
}
