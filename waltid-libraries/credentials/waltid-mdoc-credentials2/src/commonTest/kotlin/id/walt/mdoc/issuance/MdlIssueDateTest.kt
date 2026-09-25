@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.issuance

import id.walt.mdoc.objects.elements.IssuerSignedItem
import kotlinx.serialization.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class MdlIssueDateTest {

    @Test
    fun `accepts issue_date on the validFrom UTC day`() {
        requireMdlIssueDateNotAfterValidFrom(
            items("2026-09-08"),
            Instant.parse("2026-09-08T12:00:00Z"),
        )
    }

    @Test
    fun `rejects issue_date after validFrom`() {
        val error = assertFailsWith<IllegalArgumentException> {
            requireMdlIssueDateNotAfterValidFrom(
                items("2026-09-09"),
                Instant.parse("2026-09-08T12:00:00Z"),
            )
        }
        assertTrue(error.message!!.contains("issue_date"))
        assertTrue(error.message!!.contains("validFrom"))
    }

    @Test
    fun `skips namespaces other than mDL`() {
        requireMdlIssueDateNotAfterValidFrom(
            mapOf(
                "org.example" to listOf(
                    IssuerSignedItem.create(0u, "issue_date", CborString("2026-09-09")),
                )
            ),
            Instant.parse("2026-09-08T12:00:00Z"),
        )
    }

    private fun items(issueDate: String) = mapOf(
        MDL_NAMESPACE to listOf(
            IssuerSignedItem.create(0u, "issue_date", CborString(issueDate)),
        )
    )
}
