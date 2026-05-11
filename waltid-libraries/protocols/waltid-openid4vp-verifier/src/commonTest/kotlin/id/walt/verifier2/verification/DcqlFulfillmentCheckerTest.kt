package id.walt.verifier2.verification

import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class DcqlFulfillmentCheckerTest {

    private fun credentialQuery(id: String) = CredentialQuery(
        id = id,
        format = CredentialFormat.DC_SD_JWT,
        meta = SdJwtVcMeta(listOf("urn:test:$id")),
    )

    @Test
    fun noCredentialSets_missingRequiredIds_producesStructuredFailure() {
        val query = DcqlQuery(
            credentials = listOf(credentialQuery("pid"), credentialQuery("address")),
        )

        val result = DcqlFulfillmentChecker.checkOverallDcqlFulfillmentDetailed(
            dcqlQuery = query,
            successfullyValidatedQueryIds = setOf("pid"),
        )

        assertTrue(result.isFailure, "Expected failure when a required query id is missing")
        val exception = result.exceptionOrNull() ?: fail("Missing exception on failure")
        val structured = assertIs<DcqlFulfillmentChecker.StructuredDcqlFulfillmentException>(exception)
        assertEquals(listOf("address"), structured.failure.missingQueryIds)
        assertTrue(structured.failure.unsatisfiedSets.isEmpty())
        assertEquals(listOf("pid"), structured.failure.successfullyValidatedQueryIds)
    }

    @Test
    fun credentialSets_requiredSetUnsatisfied_producesUnsatisfiedSet() {
        val query = DcqlQuery(
            credentials = listOf(credentialQuery("pid"), credentialQuery("mdl"), credentialQuery("email")),
            credentialSets = listOf(
                CredentialSetQuery(options = listOf(listOf("pid"), listOf("mdl")), required = true),
            ),
        )

        val result = DcqlFulfillmentChecker.checkOverallDcqlFulfillmentDetailed(
            dcqlQuery = query,
            successfullyValidatedQueryIds = setOf("email"),
        )

        assertTrue(result.isFailure, "Expected failure when a required credential_set is unsatisfied")
        val structured = assertIs<DcqlFulfillmentChecker.StructuredDcqlFulfillmentException>(result.exceptionOrNull())
        assertTrue(structured.failure.missingQueryIds.isEmpty())
        assertEquals(1, structured.failure.unsatisfiedSets.size)
        assertEquals(listOf(listOf("pid"), listOf("mdl")), structured.failure.unsatisfiedSets[0].options)
        assertTrue(structured.failure.unsatisfiedSets[0].required)
        assertEquals(listOf("email"), structured.failure.successfullyValidatedQueryIds)
    }

    @Test
    fun credentialSets_optionalSetUnsatisfied_isNotAFailure() {
        val query = DcqlQuery(
            credentials = listOf(credentialQuery("pid"), credentialQuery("address")),
            credentialSets = listOf(
                CredentialSetQuery(options = listOf(listOf("pid")), required = true),
                CredentialSetQuery(options = listOf(listOf("address")), required = false),
            ),
        )

        val result = DcqlFulfillmentChecker.checkOverallDcqlFulfillmentDetailed(
            dcqlQuery = query,
            successfullyValidatedQueryIds = setOf("pid"),
        )

        assertTrue(result.isSuccess, "Optional unsatisfied sets must not fail DCQL fulfillment")
    }

    @Test
    fun success_noCredentialSets_allIdsPresent() {
        val query = DcqlQuery(credentials = listOf(credentialQuery("pid")))
        val result = DcqlFulfillmentChecker.checkOverallDcqlFulfillmentDetailed(
            dcqlQuery = query,
            successfullyValidatedQueryIds = setOf("pid"),
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun legacyApi_remainsBackwardCompatible() {
        val query = DcqlQuery(credentials = listOf(credentialQuery("pid")))
        val result = DcqlFulfillmentChecker.checkOverallDcqlFulfillment(
            dcqlQuery = query,
            successfullyValidatedQueryIds = emptySet(),
        )
        assertTrue(result.isFailure)
        // legacy callers only need a throwable; still get one
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
