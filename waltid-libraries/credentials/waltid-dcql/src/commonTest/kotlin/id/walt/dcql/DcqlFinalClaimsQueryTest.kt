package id.walt.dcql

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DcqlFinalClaimsQueryTest {

    @Test
    fun `claims query path is required and non empty`() {
        listOf(
            """{"credentials":[{"id":"mdl","format":"mso_mdoc","meta":{"doctype_value":"org.iso.18013.5.1.mDL"},"claims":[{}]}]}""",
            """{"credentials":[{"id":"mdl","format":"mso_mdoc","meta":{"doctype_value":"org.iso.18013.5.1.mDL"},"claims":[{"path":[]}]}]}""",
        ).forEach { json ->
            assertTrue(DcqlParser.parse(json).isFailure)
        }
    }

    @Test
    fun `mdoc claim path is exactly two string elements`() {
        listOf(
            "[\"namespace\"]",
            "[\"namespace\",\"element\",\"extra\"]",
            "[\"namespace\",1]",
        ).forEach { path ->
            val query = DcqlParser.parse(mdocQuery(path)).getOrThrow()
            assertFailsWith<IllegalArgumentException> { query.precheck() }
        }
    }

    @Test
    fun `mdoc claim path accepts namespace and data element identifier`() {
        val query = DcqlParser.parse(mdocQuery("[\"org.iso.18013.5.1\",\"given_name\"]")).getOrThrow()
        query.precheck()
    }

    private fun mdocQuery(path: String): String =
        """{"credentials":[{"id":"mdl","format":"mso_mdoc","meta":{"doctype_value":"org.iso.18013.5.1.mDL"},"claims":[{"path":$path}]}]}"""
}
