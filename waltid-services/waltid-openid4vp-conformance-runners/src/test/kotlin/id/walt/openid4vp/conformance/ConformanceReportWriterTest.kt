package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceExecutionMode
import id.walt.openid4vp.conformance.report.FullPlanEvidence
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.report.ConformanceReportFormat
import id.walt.openid4vp.conformance.report.isAccepted
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ConformanceReportWriterTest {

    @Test
    fun screenshotBlockRetainsObservedVerifierAndWalletOutcomesWithoutPassing() {
        val root = Files.createTempDirectory("observed-evidence-block").toString()
        val role = ConformanceReportWriter.Role.VP_VERIFIER
        ConformanceReportWriter.writeTestPlanResults(role, listOf(
            TestPlanResult(testName = "accepted-but-no-screenshot", conformanceTestId = "actual-test",
                conformanceStatus = "WAITING",
                verifierStatus = id.walt.verifier2.data.Verification2Session.VerificationSessionStatus.SUCCESSFUL,
                blockerReason = "Real screenshot required"),
            TestPlanResult(testName = "rejected-but-no-screenshot", conformanceTestId = "wallet-test",
                conformanceStatus = "WAITING", walletStatus = "REJECTED",
                blockerReason = "Real screenshot required"),
        ), reportRoot = root)
        val text = Files.readString(ConformanceReportWriter.reportDir(role, root).resolve("results.json"))
        val rows = kotlinx.serialization.json.Json.decodeFromString<List<ConformanceReportWriter.Entry>>(text)
        assertEquals(listOf("blocked", "blocked"), rows.map { it.status })
        assertEquals("SUCCESSFUL", rows[0].verifierStatus)
        assertEquals("REJECTED", rows[1].walletStatus)
        assertTrue(rows.none { it.accepted })
    }

    @Test
    fun legitimateSuiteSkipsRemainVisibleButCannotQualifyAnAllSkippedRun() {
        val skip = TestPlanResult(conformanceTestId = "actual-test", conformanceStatus = "FINISHED",
            conformanceResult = "SKIPPED", skipReason = "Suite skipped this module")
        assertTrue(skip.isAccepted(mode = ConformanceExecutionMode.FULL_PLAN))
        assertFalse(skip.copy(conformanceStatus = "WAITING").isAccepted(mode = ConformanceExecutionMode.FULL_PLAN))
        assertFailsWith<IllegalStateException> {
            ConformanceReportWriter.failIfNeededFromTestPlanResults(ConformanceReportWriter.Role.VCI_WALLET,
                listOf(skip), mode = ConformanceExecutionMode.FULL_PLAN)
        }
        ConformanceReportWriter.failIfNeededFromTestPlanResults(ConformanceReportWriter.Role.VCI_WALLET,
            listOf(skip, TestPlanResult(conformanceTestId = "executed-test", conformanceStatus = "FINISHED",
                conformanceResult = "PASSED")), mode = ConformanceExecutionMode.FULL_PLAN)
    }


    @Test
    fun fullPlanPreservesRepeatedModuleVariantsButReplacesThePreviousProducerRun() {
        val root = Files.createTempDirectory("full-plan-variants").toString()
        val role = ConformanceReportWriter.Role.VCI_WALLET
        val repeated = listOf(
            ConformanceReportWriter.Entry("issuance", "passed", testId = "immediate", accepted = true),
            ConformanceReportWriter.Entry("issuance", "blocked", testId = "deferred", accepted = false),
        )
        ConformanceReportWriter.write(role, repeated, root, producer = "wallet", mode = ConformanceExecutionMode.FULL_PLAN)
        val results = Files.readString(ConformanceReportWriter.reportDir(role, root).resolve("results.json"))
        assertTrue(results.contains("immediate"))
        assertTrue(results.contains("deferred"))
        val summary = Files.readString(ConformanceReportWriter.reportDir(role, root).resolve("summary.md"))
        assertTrue(summary.contains("- Total: 2"))
        assertTrue(summary.contains("- Failed: 1"))
        ConformanceReportWriter.write(role, listOf(repeated.first()), root, producer = "wallet", mode = ConformanceExecutionMode.FULL_PLAN)
        assertFalse(Files.readString(ConformanceReportWriter.reportDir(role, root).resolve("results.json")).contains("deferred"))
    }


    @Test
    fun fullPlanRefusesToDiscardMalformedPriorEvidence() {
        val root = Files.createTempDirectory("malformed-full-plan")
        val dir = ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_WALLET, root.toString())
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("results.json"), "interrupted write")
        assertFailsWith<IllegalStateException> {
            ConformanceReportWriter.write(ConformanceReportWriter.Role.VP_WALLET, emptyList(), root.toString(),
                producer = "wallet", mode = ConformanceExecutionMode.FULL_PLAN)
        }
        assertEquals("interrupted write", Files.readString(dir.resolve("results.json")))
    }


    @Test
    fun suiteInapplicabilityIsNotCountedAsAnExecutedPass() {
        val root = Files.createTempDirectory("full-plan-summary").toString()
        ConformanceReportWriter.write(ConformanceReportWriter.Role.VP_WALLET, listOf(
            ConformanceReportWriter.Entry("executed", "passed", accepted = true),
            ConformanceReportWriter.Entry("excluded-by-suite", "not-applicable", accepted = true),
        ), root, mergeExisting = false)
        val summary = Files.readString(ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_WALLET, root).resolve("summary.md"))
        assertTrue(summary.contains("- Passed: 1"))
        assertTrue(summary.contains("- Skipped (not applicable to this variant): 1"))
    }


    @Test
    fun fullPlanInventoryRetainsUnfinishedModulesAndExcludesConfigurationSecrets() {
        val root = Files.createTempDirectory("full-plan-inventory")
        val server = buildJsonObject { put("version", "5.3.4"); put("revision", "23353b3") }
        val modules = listOf("first-module", "second-module").map { CreateTestPlanResponse.Module(it) }
        val plan = CreateTestPlanResponse("plan-id", "haip-plan", modules)
        FullPlanEvidence.write(server, plan, null, buildJsonObject { put("private_key", "never-export-this") }, root.toString())
        val inventory = Files.readString(root.resolve("plans/plan-id.json"))
        assertTrue(inventory.contains("full-plan"))
        assertTrue(inventory.contains(FullPlanEvidence.SUITE_REVISION))
        assertTrue(inventory.contains("second-module"))
        assertFalse(inventory.contains("never-export-this"))
        FullPlanEvidence.record(plan.id, 0, modules[0], "executed", "test-id", "FINISHED", "FAILED",
            mode = ConformanceExecutionMode.FULL_PLAN, reportRoot = root.toString())
        assertTrue(Files.readString(root.resolve("coverage/plan-id/0.json")).contains("FAILED"))
        assertTrue(Files.readString(root.resolve("coverage/plan-id/1.json")).contains("No execution result recorded"))
        assertFailsWith<IllegalStateException> {
            FullPlanEvidence.write(server, plan.copy(id = "empty-plan", modules = emptyList()), null,
                buildJsonObject {}, root.toString())
        }
        assertTrue(Files.exists(root.resolve("plans/empty-plan.json")))
    }


    @Test
    fun fullPlanAcceptanceRequiresAnExecutedSuitePass() {
        val pass = TestPlanResult(conformanceTestId = "test-id", conformanceStatus = "FINISHED",
            conformanceResult = "PASSED", walletStatus = "PASSED")
        assertTrue(pass.isAccepted(mode = ConformanceExecutionMode.FULL_PLAN))
        for (result in listOf(
            pass.copy(conformanceResult = "WARNING"), pass.copy(conformanceResult = "REVIEW"),
            pass.copy(conformanceResult = "FAILED"), pass.copy(conformanceStatus = "WAITING"),
            pass.copy(conformanceStatus = null), pass.copy(errorMessage = "adapter failed"),
            pass.copy(conformanceTestId = "N/A"), pass.copy(conformanceTestId = ""), pass.copy(conformanceTestId = " "),
            pass.copy(blockerReason = "Real result screen required"),
            pass.copy(walletStatus = "ERROR"), pass.copy(skipReason = "FAPI client entry point missing"),
            pass.copy(skipReason = "Suite skipped this module"),
        )) assertFalse(result.isAccepted(mode = ConformanceExecutionMode.FULL_PLAN), result.toString())
        assertTrue(pass.copy(conformanceResult = "REVIEW").isAccepted(mode = ConformanceExecutionMode.CI))
        assertTrue(pass.copy(skipReason = "Not applicable to this variant: no request_method value applies")
            .isAccepted(mode = ConformanceExecutionMode.FULL_PLAN))
    }

    @Test
    fun fullPlanRequiresThePinnedRunningSuiteRatherThanOnlyAVersionLabel() {
        FullPlanEvidence.requirePinnedSuite(buildJsonObject { put("version", "5.3.4"); put("revision", "23353b3") })
        for ((version, revision) in listOf("5.2.4" to "23353b3", "5.3.4" to "db1080a", "5.3.4" to "2")) {
            assertFailsWith<IllegalArgumentException> {
                FullPlanEvidence.requirePinnedSuite(buildJsonObject { put("version", version); put("revision", revision) })
            }
        }
        assertFailsWith<IllegalArgumentException> { FullPlanEvidence.requirePinnedSuite(buildJsonObject { put("version", "5.3.4") }) }
    }


    @Test
    fun writesSummaryAndResultsForRole() {
        val reportRoot = Files.createTempDirectory("openid-conformance-report").toString()
        val results = listOf(
            TestPlanResult(
                testName = "MdlBaseline",
                conformanceTestId = "abc-123",
                conformanceStatus = "FINISHED",
                conformanceResult = "PASSED",
            ),
            TestPlanResult(
                testName = "SdJwtHaip",
                conformanceTestId = "def-456",
                conformanceStatus = "FINISHED",
                conformanceResult = "FAILED",
                errorMessage = "audience mismatch",
            ),
        )

        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_VERIFIER,
            results = results,
            conformanceHost = "conformance.example",
            conformancePort = 443,
            reportRoot = reportRoot,
        )

        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_VERIFIER, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("MdlBaseline"))
        assertTrue(summary.contains("SdJwtHaip"))
        assertTrue(summary.contains("audience mismatch"))
        assertTrue(summary.contains("[log](https://conformance.example:443/log-detail.html?log=def-456)"))
        assertTrue(summary.contains("# OpenID4VP Verifier Conformance Summary"))
        assertTrue(summary.contains("| Test | Status | Suite | Log | Error |"))
        assertTrue(summary.contains("<details>"))
        assertTrue(summary.contains("<summary>OpenID4VP Verifier results</summary>"))
        assertTrue(summary.contains("</details>"))
        assertFalse(summary.contains("## Failed and skipped"))
        assertFalse(summary.contains("- Fix:"))
        assertFalse(summary.contains("Soft-fail"))
        assertFalse(summary.contains("CONFORMANCE_ALLOW_FAILURE"))
    }

    @Test
    fun writesTheSameSummaryShapeForWalletRoles() {
        val reportRoot = Files.createTempDirectory("openid-conformance-wallet-report").toString()
        val results = listOf(
            TestPlanResult(
                testName = "wallet-module",
                conformanceTestId = "wal-123",
                conformanceStatus = "FINISHED",
                conformanceResult = "PASSED",
                walletStatus = "PASSED",
            ),
        )

        listOf(ConformanceReportWriter.Role.VP_WALLET, ConformanceReportWriter.Role.VCI_WALLET)
            .forEach { role ->
                ConformanceReportWriter.writeTestPlanResults(
                    role = role,
                    results = results,
                    conformanceHost = "conformance.example",
                    conformancePort = 443,
                    reportRoot = reportRoot,
                    producer = role.directoryName,
                )
                val summary = Files.readString(
                    ConformanceReportWriter.reportDir(role, reportRoot).resolve("summary.md")
                )
                assertTrue(summary.contains("# ${role.title} Conformance Summary"))
                assertTrue(summary.contains("| Test | Status | Suite | Log | Error |"))
                assertTrue(summary.contains("<details>"))
                assertTrue(summary.contains("<summary>${role.title} results</summary>"))
                assertTrue(summary.contains("wallet-module"))
            }
    }

    @Test
    fun writeUnavailableIfEmptyDoesNotOverwriteAnExistingReport() {
        val reportRoot = Files.createTempDirectory("openid-conformance-skipped").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_WALLET,
            results = listOf(
                TestPlanResult(
                    testName = "real-module",
                    conformanceTestId = "abc",
                    conformanceResult = "PASSED",
                    walletStatus = "PASSED",
                )
            ),
            reportRoot = reportRoot,
            producer = "vp-wallet",
        )
        ConformanceReportWriter.writeUnavailableIfEmpty(
            role = ConformanceReportWriter.Role.VP_WALLET,
            reason = "suite down",
            reportRoot = reportRoot,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("real-module"))
        assertFalse(summary.contains("suite down"))
    }

    @Test
    fun writeUnavailableIfEmptyPublishesPlaceholderWhenMissing() {
        val reportRoot = Files.createTempDirectory("openid-conformance-placeholder").toString()
        ConformanceReportWriter.writeUnavailableIfEmpty(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            reason = "Conformance suite not available at localhost.emobix.co.uk:8443",
            reportRoot = reportRoot,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VCI_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("# OpenID4VCI Wallet Conformance Summary"))
        assertTrue(summary.contains("| Test | Status | Suite | Log | Error |"))
        assertTrue(summary.contains("conformance-suite"))
        assertTrue(summary.contains("not available"))
        assertFalse(summary.contains("## Failed and skipped"))
        assertFalse(summary.contains("- Fix:"))
    }

    @Test
    fun failIfNeededFailsOnUnacceptedResults() {
        val failing = listOf(
            TestPlanResult(
                testName = "failing",
                conformanceTestId = "x",
                conformanceResult = "FAILED",
            )
        )
        val passing = listOf(
            TestPlanResult(
                testName = "passing",
                conformanceTestId = "y",
                conformanceResult = "PASSED",
            )
        )
        listOf(
            ConformanceReportWriter.Role.VP_VERIFIER,
            ConformanceReportWriter.Role.VCI_WALLET,
            ConformanceReportWriter.Role.VCI_ISSUER,
        ).forEach { role ->
            ConformanceReportWriter.failIfNeededFromTestPlanResults(
                role = role,
                results = passing,
            )
            assertFailsWith<IllegalStateException> {
                ConformanceReportWriter.failIfNeededFromTestPlanResults(
                    role = role,
                    results = failing,
                )
            }
        }
    }

    @Test
    fun compactWalletNamesStayInTheSameTable() {
        val reportRoot = Files.createTempDirectory("openid-conformance-compact").toString()
        val producerId =
            "oid4vp-1final-wallet-test-plan/client_id_prefix=x509_hash,credential_format=sd_jwt_vc," +
                "request_method=request_uri_signed,response_mode=direct_post.jwt,vp_profile=plain"
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_WALLET,
            results = listOf(
                TestPlanResult(
                    testName = "$producerId/oid4vp-1final-wallet-happy-flow",
                    conformanceTestId = "pass-1",
                    conformanceResult = "PASSED",
                    walletStatus = "PASSED",
                ),
                TestPlanResult(
                    testName = "$producerId/oid4vp-1final-wallet-negative-test-missing-nonce",
                    conformanceTestId = "skip-1",
                    conformanceResult = "SKIPPED",
                    walletStatus = "SKIPPED",
                    skipReason = "Not applicable to this variant: no request_method value applies to this variant",
                ),
                TestPlanResult(
                    testName = "$producerId/oid4vp-1final-wallet-happy-flow-encrypted",
                    conformanceTestId = "fail-1",
                    conformanceResult = "FAILED",
                    walletStatus = "FAILED",
                    errorMessage = "audience mismatch",
                ),
            ),
            conformanceHost = "conformance.example",
            conformancePort = 443,
            reportRoot = reportRoot,
            producer = producerId,
        )

        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_WALLET, reportRoot)
                .resolve("summary.md")
        )
        val compact = ConformanceReportFormat.displayName(
            "$producerId/oid4vp-1final-wallet-happy-flow",
        )
        assertEquals("happy-flow", compact.title)
        assertEquals(
            "sd_jwt_vc · x509_hash · request_uri_signed · direct_post.jwt · plain",
            compact.variant,
        )
        assertTrue(summary.contains("happy-flow"))
        assertTrue(summary.contains("negative-test-missing-nonce"))
        assertTrue(summary.contains(compact.variant!!))
        assertTrue(summary.contains("| Test | Status | Suite | Log | Error |"))
        assertTrue(summary.contains("<details>"))
        assertTrue(summary.contains("<summary>OpenID4VP Wallet results</summary>"))
        assertTrue(summary.contains("`skipped`"))
        assertTrue(summary.contains("`failed`"))
        assertTrue(summary.contains("audience mismatch"))
        assertTrue(summary.contains("Not applicable to this variant"))
        assertFalse(summary.contains("## Failed and skipped"))
        assertFalse(summary.contains("- Fix:"))
        assertFalse(summary.contains("| `$producerId/oid4vp-1final-wallet-happy-flow` |"))
    }

    @Test
    fun vciTlsWarningIsAcceptedWhileKeepingTheSuiteResult() {
        val warning = TestPlanResult(
            testName = "oid4vci-1_0-wallet-test-credential-issuance",
            conformanceTestId = "warn-1",
            conformanceResult = "WARNING",
            walletStatus = "PASSED",
        )
        assertTrue(warning.isAccepted())
        assertTrue(warning.passed)
        assertEquals(null, warning.message)

        val reportRoot = Files.createTempDirectory("openid-conformance-vci-warning").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            results = listOf(warning),
            conformanceHost = "conformance.example",
            conformancePort = 443,
            reportRoot = reportRoot,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VCI_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("`passed`"))
        assertTrue(summary.contains("WARNING"))
        assertFalse(summary.contains("`failed`"))
        assertFalse(summary.contains("Conformance: WARNING"))
    }

    @Test
    fun vpWalletReviewIsAcceptedWithoutAnErrorCell() {
        val review = TestPlanResult(
            testName = "oid4vp-1final-wallet-happy-flow",
            conformanceTestId = "review-1",
            conformanceResult = "REVIEW",
            walletStatus = "PASSED",
        )
        assertTrue(review.isAccepted())
        assertTrue(review.passed)

        val reportRoot = Files.createTempDirectory("openid-conformance-vp-review").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_WALLET,
            results = listOf(review),
            conformanceHost = "conformance.example",
            conformancePort = 443,
            reportRoot = reportRoot,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("`passed`"))
        assertTrue(summary.contains("REVIEW"))
        assertFalse(summary.contains("`failed`"))
        assertFalse(summary.contains("Conformance: REVIEW"))
    }

    @Test
    fun interruptedWarningIsNotAcceptedAsAPass() {
        val interrupted = TestPlanResult(
            testName = "oid4vci-1_0-wallet-test-client-attestation-challenge",
            conformanceTestId = "int-1",
            conformanceStatus = "INTERRUPTED",
            conformanceResult = "WARNING",
            walletStatus = "WARNING",
            errorMessage = "Suite interrupted this module",
        )
        assertFalse(interrupted.isAccepted())
        assertFalse(interrupted.passed)

        val reportRoot = Files.createTempDirectory("openid-conformance-vci-interrupted").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            results = listOf(interrupted),
            reportRoot = reportRoot,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VCI_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("`failed`"))
        assertTrue(summary.contains("Suite interrupted this module"))
    }

    @Test
    fun skippedFapi2ClientModuleIsAccepted() {
        val skipped = TestPlanResult(
            testName = "fapi2-security-profile-final-client-test-happy-path",
            conformanceTestId = "fapi-1",
            conformanceResult = "SKIPPED",
            walletStatus = "SKIPPED",
            skipReason = "FAPI 2.0 client test: needs wallet-initiated issuance, which the " +
                "harness cannot yet trigger",
        )
        assertTrue(skipped.isAccepted())

        val reportRoot = Files.createTempDirectory("openid-conformance-fapi2-skip").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            results = listOf(skipped),
            reportRoot = reportRoot,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VCI_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("`skipped`"))
        assertTrue(summary.contains("wallet-initiated issuance"))
        assertFalse(summary.contains("`failed`"))
    }
}
