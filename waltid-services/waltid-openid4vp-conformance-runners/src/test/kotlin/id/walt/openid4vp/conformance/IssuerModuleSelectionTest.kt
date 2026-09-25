package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.runner.IssuerModuleSelection
import id.walt.openid4vp.conformance.testplans.requireExecutedBatchIssuance
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantModuleRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunStatus
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerBatchCoverageStatus
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantMatrix
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantSelection
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantReportWriter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun metadataGroupDoesNotSelectFapiDiscoveryModule() {
        val module = "fapi2-security-profile-final-discovery-end-point-verification"

        assertTrue(!IssuerModuleSelection(groups = setOf("metadata")).matches(module))
        assertTrue(IssuerModuleSelection(groups = setOf("fapi")).matches(module))
    }

    @Test
    fun batchIssuanceBelongsToPositiveGroup() {
        val module = "oid4vci-1_0-issuer-batch-issuance"

        assertTrue(IssuerModuleSelection(groups = setOf("positive")).matches(module))
        assertTrue(!IssuerModuleSelection(groups = setOf("metadata")).matches(module))
    }

    @Test
    fun batchAcceptanceRequiresAnExecutedPassForEveryVariant() {
        val passed = batchVariant("sdjwt")
        requireExecutedBatchIssuance(listOf(passed, batchVariant("mdoc")))
        for (module in listOf(
            passed.modules.single().copy(result = "SKIPPED"),
            passed.modules.single().copy(result = "FAILED", accepted = false),
            passed.modules.single().copy(status = "WAITING"),
            passed.modules.single().copy(status = "INTERRUPTED", result = "SKIPPED"),
            passed.modules.single().copy(testId = null),
            passed.modules.single().copy(testId = " "),
            passed.modules.single().copy(accepted = false),
            passed.modules.single().copy(error = "Delivery failed"),
        )) {
            assertFailsWith<IllegalArgumentException> {
                requireExecutedBatchIssuance(listOf(passed, batchVariant("mdoc").copy(modules = listOf(module))))
            }
        }
    }

    @Test
    fun batchAcceptanceRejectsExcludedUnselectedAndEmptyRuns() {
        assertFailsWith<IllegalArgumentException> { requireExecutedBatchIssuance(emptyList()) }
        for (status in IssuerVariantRunStatus.entries) {
            assertFailsWith<IllegalArgumentException> {
                requireExecutedBatchIssuance(listOf(batchVariant("missing").copy(status = status, modules = emptyList())))
            }
        }
        val happyFlowOnly = batchVariant("not-selected").let {
            it.copy(modules = listOf(it.modules.single().copy(testModule = "oid4vci-1_0-issuer-happy-flow")))
        }
        assertFailsWith<IllegalArgumentException> { requireExecutedBatchIssuance(listOf(happyFlowOnly)) }
    }

    @Test
    fun batchAcceptanceDoesNotRejectOtherLegitimateCapabilitySkips() {
        val variant = batchVariant("sdjwt")
        requireExecutedBatchIssuance(listOf(variant.copy(modules = variant.modules +
            variant.modules.single().copy(testModule = "oid4vci-1_0-issuer-metadata-test-signed", result = "SKIPPED"))))
    }

    @Test
    fun combinedMatrixRequiresSixteenBatchPassesAndReportsFourUnavailableVariants() {
        val variants = IssuerVariantSelection(
            fapiProfiles = setOf("vci", "vci_haip"),
            clientAuthTypes = setOf("client_attestation"),
            senderConstrains = setOf("dpop"),
            authorizationRequestTypes = setOf("simple"),
            requestMethods = setOf("unsigned"),
        ).select(IssuerVariantMatrix.all())
        assertEquals(20, variants.size)
        val results = variants.map { variant ->
            val result = batchVariant(variant.id).copy(variant = variant.toJsonObject())
            if (variant.isHaip && variant.credentialEncryption == "encrypted") {
                result.copy(modules = listOf(result.modules.single().copy(testModule = "oid4vci-1_0-issuer-happy-flow")))
            } else result
        }
        requireExecutedBatchIssuance(results)
        assertEquals(16, results.count { it.batchCoverage == IssuerBatchCoverageStatus.PASSED })
        assertEquals(4, results.count { it.batchCoverage == IssuerBatchCoverageStatus.NOT_OFFERED_BY_PINNED_SUITE })
        val summary = IssuerVariantReportWriter.buildSummary(results)
        assertTrue(summary.contains("16 passed; 0 missing; 0 not_passed; 4 not_offered_by_pinned_suite"))
        assertTrue(summary.contains("it is not batch coverage"))
        assertTrue(summary.contains("| Batch coverage |"))
        results.filter { it.batchCoverage == IssuerBatchCoverageStatus.PASSED }.forEach { result ->
            val missing = result.copy(modules = emptyList())
            val error = assertFailsWith<IllegalArgumentException> {
                requireExecutedBatchIssuance(results.map { if (it == result) missing else it })
            }
            assertTrue(error.message.orEmpty().contains(result.variantId))
        }
    }

    @Test
    fun unavailableBatchIsNarrowlyScopedAndCannotHideActualBatchResults() {
        val encryptedHaip = batchVariant("haip-encrypted").copy(variant = buildJsonObject {
            put("fapi_profile", "vci_haip")
            put("vci_grant_type", "authorization_code")
            put("vci_credential_encryption", "encrypted")
        })
        // If the suite does produce a batch result, validate it even in this combination.
        requireExecutedBatchIssuance(listOf(encryptedHaip))
        for (result in listOf("SKIPPED", "FAILED")) {
            assertFailsWith<IllegalArgumentException> {
                requireExecutedBatchIssuance(listOf(encryptedHaip.copy(
                    modules = listOf(encryptedHaip.modules.single().copy(result = result))
                )))
            }
        }
        for ((field, value) in listOf(
            "fapi_profile" to "vci", "vci_credential_encryption" to "plain",
            "vci_grant_type" to "pre_authorization_code",
        )) {
            val outsideException = encryptedHaip.copy(
                variant = buildJsonObject { encryptedHaip.variant.forEach { (k, v) -> put(k, v) }; put(field, value) },
                modules = emptyList(),
            )
            assertEquals(IssuerBatchCoverageStatus.MISSING, outsideException.batchCoverage)
            assertFailsWith<IllegalArgumentException> { requireExecutedBatchIssuance(listOf(outsideException)) }
        }
    }

    @Test
    fun batchAcceptanceRejectsZeroCoverageAndUnsuccessfulEncryptedHaipVariants() {
        val unavailable = batchVariant("haip-encrypted").copy(
            variant = buildJsonObject {
                put("fapi_profile", "vci_haip")
                put("vci_grant_type", "authorization_code")
                put("vci_credential_encryption", "encrypted")
            },
            modules = emptyList(),
        )
        val noCoverage = assertFailsWith<IllegalArgumentException> { requireExecutedBatchIssuance(listOf(unavailable)) }
        assertTrue(noCoverage.message.orEmpty().contains("No batch coverage"))
        for (status in IssuerVariantRunStatus.entries.filter { it != IssuerVariantRunStatus.PASSED }) {
            assertFailsWith<IllegalArgumentException> {
                requireExecutedBatchIssuance(listOf(batchVariant("basic"), unavailable.copy(status = status)))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            requireExecutedBatchIssuance(listOf(batchVariant("basic"), unavailable.copy(error = "Connection failed")))
        }
    }

    private fun batchVariant(id: String) = IssuerVariantRunResult(
        variantId = id,
        variant = JsonObject(emptyMap()),
        status = IssuerVariantRunStatus.PASSED,
        modules = listOf(IssuerVariantModuleRunResult(
            testModule = "oid4vci-1_0-issuer-batch-issuance",
            testId = "batch-$id",
            status = "FINISHED",
            result = "PASSED",
            accepted = true,
        )),
    )
}
