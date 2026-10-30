package id.walt.dcql

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        for (alternatives in listOf(
            listOf(listOf("name"), listOf("name")),
            listOf(emptyList()),
        )) {
            val query = DcqlQuery(
                credentials = listOf(
                    credential(
                        "pid",
                        claims = listOf(ClaimsQuery(id = "name", pathStrings = listOf("name"))),
                    ).copy(claimSets = alternatives)
                )
            )
            assertFailsWith<IllegalArgumentException> { query.precheck() }
        }
    }

    @Test
    fun `dc+sd-jwt query decodes and encodes with the same identifier`() {
        val query = json.decodeFromString<DcqlQuery>(queryWithFormat("dc+sd-jwt"))

        assertEquals(CredentialFormat.DC_SD_JWT, query.credentials.single().format)
        assertTrue("\"format\":\"dc+sd-jwt\"" in json.encodeToString(DcqlQuery.serializer(), query))
    }

    @Test
    fun `legacy vc+sd-jwt query is rejected with a hint to use dc+sd-jwt`() {
        val message = assertFailsWith<SerializationException> {
            json.decodeFromString<DcqlQuery>(queryWithFormat("vc+sd-jwt"))
        }.message.orEmpty()

        assertTrue("Unsupported DCQL credential format \"vc+sd-jwt\"" in message, message)
        assertTrue("must use \"dc+sd-jwt\"" in message, message)
    }

    @Test
    fun `unknown format is rejected with the recognized formats`() {
        val message = assertFailsWith<SerializationException> {
            json.decodeFromString<DcqlQuery>(queryWithFormat("unknown-format"))
        }.message.orEmpty()

        assertTrue("Unsupported DCQL credential format \"unknown-format\"" in message, message)
        assertTrue("Recognized formats: jwt_vc_json, ldp_vc, mso_mdoc, dc+sd-jwt, ac_vp" in message, message)
        assertTrue("must use" !in message, message)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun queryWithFormat(format: String) =
        """{"credentials":[{"id":"q1","format":"$format","meta":{"vct_values":["https://example.com/pid"]}}]}"""

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
