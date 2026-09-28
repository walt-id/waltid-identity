package id.walt.dcql

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DcqlQueryInvariantTest {

    @Test
    fun `claims paths accept strings non-negative integers and null wildcards`() {
        ClaimsQuery(path = listOf(JsonPrimitive("people"), JsonPrimitive(0), JsonNull))

        assertFailsWith<IllegalArgumentException> {
            ClaimsQuery(path = listOf(JsonPrimitive(-1)))
        }
        assertFailsWith<IllegalArgumentException> {
            ClaimsQuery(path = listOf(JsonPrimitive(true)))
        }
    }

    @Test
    fun `credential set alternatives are unique independent of order`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialSetQuery(options = listOf(listOf("pid", "address"), listOf("address", "pid")))
        }
    }

    @Test
    fun `dcql precheck rejects duplicate credential and claim identifiers`() {
        val duplicateCredentialIds = DcqlQuery(
            credentials = listOf(
                credential("pid"),
                credential("pid"),
            )
        )
        assertFailsWith<IllegalArgumentException> { duplicateCredentialIds.precheck() }

        val duplicateClaimIds = DcqlQuery(
            credentials = listOf(
                credential(
                    "pid",
                    claims = listOf(
                        ClaimsQuery(id = "name", pathStrings = listOf("name")),
                        ClaimsQuery(id = "name", pathStrings = listOf("family_name")),
                    ),
                )
            )
        )
        assertFailsWith<IllegalArgumentException> { duplicateClaimIds.precheck() }
    }

    @Test
    fun `dcql precheck rejects identifiers outside OpenID4VP character set`() {
        val invalidCredentialId = DcqlQuery(
            credentials = listOf(credential("urn:eudi:pid:1")),
        )
        assertFailsWith<IllegalArgumentException> { invalidCredentialId.precheck() }

        val dottedCredentialId = DcqlQuery(
            credentials = listOf(credential("eu.europa.ec.eudi.pid.1")),
        )
        assertFailsWith<IllegalArgumentException> { dottedCredentialId.precheck() }

        val invalidClaimId = DcqlQuery(
            credentials = listOf(
                credential(
                    "pid",
                    claims = listOf(
                        ClaimsQuery(id = "address.street_address", pathStrings = listOf("address", "street_address")),
                    ),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> { invalidClaimId.precheck() }
    }

    @Test
    fun `dcql precheck accepts alphanumeric underscore and hyphen identifiers`() {
        DcqlQuery(
            credentials = listOf(
                credential(
                    "eudi-pid-1",
                    claims = listOf(
                        ClaimsQuery(id = "given_name", pathStrings = listOf("given_name")),
                        ClaimsQuery(pathStrings = listOf("address", "street_address")),
                    ),
                ),
            ),
        ).precheck()
    }

    @Test
    fun `dcql precheck rejects empty and duplicate claim set alternatives`() {
        val query = DcqlQuery(
            credentials = listOf(
                credential(
                    "pid",
                    claims = listOf(ClaimsQuery(id = "name", pathStrings = listOf("name"))),
                ).copy(claimSets = listOf(listOf("name"), listOf("name")))
            )
        )
        assertFailsWith<IllegalArgumentException> { query.precheck() }
    }

    private fun credential(
        id: String,
        claims: List<ClaimsQuery>? = null,
    ) = CredentialQuery(
        id = id,
        format = CredentialFormat.DC_SD_JWT,
        meta = NoMeta,
        claims = claims,
    )
}
