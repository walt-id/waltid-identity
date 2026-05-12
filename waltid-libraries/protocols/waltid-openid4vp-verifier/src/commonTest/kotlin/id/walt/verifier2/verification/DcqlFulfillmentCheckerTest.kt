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

        val failure = assertIs<DcqlFulfillmentChecker.DcqlFulfillmentCheckResult.Failure>(result)
        assertEquals(listOf("address"), failure.details.missingQueryIds)
        assertTrue(failure.details.unsatisfiedSets.isEmpty())
        assertEquals(listOf("pid"), failure.details.successfullyValidatedQueryIds)
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

        val failure = assertIs<DcqlFulfillmentChecker.DcqlFulfillmentCheckResult.Failure>(result)
        assertTrue(failure.details.missingQueryIds.isEmpty())
        assertEquals(1, failure.details.unsatisfiedSets.size)
        assertEquals(listOf(listOf("pid"), listOf("mdl")), failure.details.unsatisfiedSets[0].options)
        assertEquals(listOf("email"), failure.details.successfullyValidatedQueryIds)
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

        assertIs<DcqlFulfillmentChecker.DcqlFulfillmentCheckResult.Success>(result)
    }

    @Test
    fun success_noCredentialSets_allIdsPresent() {
        val query = DcqlQuery(credentials = listOf(credentialQuery("pid")))
        val result = DcqlFulfillmentChecker.checkOverallDcqlFulfillmentDetailed(
            dcqlQuery = query,
            successfullyValidatedQueryIds = setOf("pid"),
        )
        assertIs<DcqlFulfillmentChecker.DcqlFulfillmentCheckResult.Success>(result)
    }

    @Test
    @Suppress("DEPRECATION")
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
