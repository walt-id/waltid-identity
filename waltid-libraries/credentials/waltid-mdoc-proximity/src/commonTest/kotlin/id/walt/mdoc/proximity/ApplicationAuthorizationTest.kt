package id.walt.mdoc.proximity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ApplicationAuthorizationTest {
    @Test
    fun `validated application authorization is one invariant-bearing preview value`() {
        val authorization = authorization()

        assertEquals("org.example.authorization:v1", authorization.profileId)
        assertEquals("Confirm example authorization", authorization.displayTitle)
        assertEquals(listOf("amount", "recipient"), authorization.details.map { it.id })

        assertFailsWith<IllegalArgumentException> {
            MdocApplicationAuthorization("", "Title", authorization.details, digest(1))
        }
        assertFailsWith<IllegalArgumentException> {
            MdocApplicationAuthorization("profile:v1", "", authorization.details, digest(1))
        }
        assertFailsWith<IllegalArgumentException> {
            MdocApplicationAuthorization("profile:v1", "Title", emptyList(), digest(1))
        }
        assertFailsWith<IllegalArgumentException> {
            MdocApplicationAuthorization(
                "profile:v1",
                "Title",
                listOf(
                    MdocApplicationAuthorizationDetail("duplicate", "First", "one"),
                    MdocApplicationAuthorizationDetail("duplicate", "Second", "two"),
                ),
                digest(1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MdocApplicationAuthorization(
                "profile:v1",
                "Title",
                authorization.details,
                ImmutableBytes.of(byteArrayOf(1)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MdocApplicationAuthorizationDetail("", "Label", "Value")
        }
        assertFailsWith<IllegalArgumentException> {
            MdocApplicationAuthorizationDetail("id", "", "Value")
        }
        assertFailsWith<IllegalArgumentException> {
            MdocApplicationAuthorizationDetail("id", "Label", "")
        }
    }

    @Test
    fun `consent binding covers display-safe details and opaque profile result`() {
        val original = authorization()

        assertEquals(original.consentBindingDigest(), authorization().consentBindingDigest())
        assertNotEquals(
            original.consentBindingDigest(),
            authorization(value = "EUR 43.00").consentBindingDigest(),
        )
        assertNotEquals(
            original.consentBindingDigest(),
            authorization(resultDigest = digest(2)).consentBindingDigest(),
        )
    }

    private fun authorization(
        value: String = "EUR 42.00",
        resultDigest: ImmutableBytes = digest(1),
    ) = MdocApplicationAuthorization(
        profileId = "org.example.authorization:v1",
        displayTitle = "Confirm example authorization",
        details = listOf(
            MdocApplicationAuthorizationDetail("amount", "Amount", value),
            MdocApplicationAuthorizationDetail("recipient", "Recipient", "Example Shop"),
        ),
        resultBindingDigest = resultDigest,
    )

    private fun digest(value: Byte): ImmutableBytes = ImmutableBytes.of(ByteArray(32) { value })
}
