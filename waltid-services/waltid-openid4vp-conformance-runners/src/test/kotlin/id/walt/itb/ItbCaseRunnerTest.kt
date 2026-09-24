package id.walt.itb

import id.walt.openid4vci.errors.CredentialError
import id.walt.wallet2.handlers.CredentialEndpointException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ItbCaseRunnerTest {
    private val suite = ItbCatalogue.initialWalletCases().suites.first()
    private val case = suite.cases.single { it.id == "tc_vci_006" }
    private val session = "00000000-0000-0000-0000-000000000001"
    private val report = javaClass.getResource("/itb/vci006-success.xml")!!.readText()
    private fun status(complete: Boolean, verdict: String = "SUCCESS") =
        """{"sessions":[{"session":"$session","result":"$verdict","startTime":"2026-09-21T14:48:14Z"${if (complete) ",\"endTime\":\"2026-09-21T14:49:58Z\"" else ""}}]}"""

    private inner class Bridge : ItbInteractionBridge {
        var reads = 0
        var completed = false
        var readFailure: Exception? = null
        override suspend fun prepare(suite: ItbCatalogue.Suite, case: ItbCatalogue.Case) =
            ItbSession(suite.id, case.id, session)
        override suspend fun read(session: ItbSession): ItbWalletInteraction {
            reads++
            readFailure?.let { throw it }
            return ItbWalletInteraction.Offer(Url("openid-credential-offer://?credential_offer=%7B%7D"))
        }
        override suspend fun complete() { completed = true }
    }

    @Test
    fun runningSuccessDoesNotBecomeAPassBeforeWalletAndTerminalReport() = runBlocking<Unit> {
        var statusCalls = 0
        var walletCalls = 0
        val bridge = Bridge()
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "status" -> status(++statusCalls >= 2)
                session -> report
                else -> error("Unexpected request")
            }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val runner = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge, { walletCalls++ }, pollMillis = 1)
            val result = runner.run(suite, case)
            assertEquals(ItbCaseResult.Outcome.PASSED, result.outcome)
            assertEquals(2, statusCalls)
            assertEquals(1, walletCalls)
            assertTrue(bridge.completed)
            assertTrue(result.walletSucceeded && result.testBedCompleted)
        }
    }

    @Test
    fun walletFailureRetainsTheActualVerdictAndStopsOnlyItsOwnSession() = runBlocking<Unit> {
        var stopped = false
        val bridge = Bridge()
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "status" -> status(false, "UNDEFINED")
                "stop" -> { stopped = true; "" }
                session -> report.replace("<result>SUCCESS</result>", "<result>UNDEFINED</result>")
                else -> error("Unexpected request")
            }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val runner = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge, {
                throw IllegalStateException("sensitive protocol payload")
            })
            val result = runner.run(suite, case)
            assertEquals(ItbCaseResult.Outcome.WALLET_FAILED, result.outcome)
            assertEquals(ItbSessionReport.Verdict.UNDEFINED, result.testBedVerdict)
            assertEquals("IllegalStateException", result.errorType)
            assertFalse(result.toString().contains("sensitive protocol payload"))
            assertTrue(stopped)
            assertFalse(bridge.completed)
            assertTrue(result.adapterInvoked)
            assertFalse(result.walletSucceeded)
        }
    }

    @Test
    fun credentialEndpointFailureReportsOnlyBoundedProtocolCodes() = runBlocking<Unit> {
        for ((issuerCode, expected) in listOf(
            "invalid_proof" to "credential_endpoint_http_400_invalid_proof",
            "private_response_value" to "credential_endpoint_http_400",
        )) {
            HttpClient(MockEngine { request ->
                respond(when (request.url.encodedPath.substringAfterLast('/')) {
                    "status" -> status(false, "UNDEFINED")
                    "stop" -> ""
                    session -> report.replace("<result>SUCCESS</result>", "<result>UNDEFINED</result>")
                    else -> error("Unexpected request")
                })
            }).use { client ->
                val result = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), Bridge(), {
                    throw CredentialEndpointException(400, CredentialError(issuerCode, "private response details"))
                }).run(suite, case)
                assertEquals(ItbCaseResult.Outcome.WALLET_FAILED, result.outcome)
                assertEquals("CredentialEndpointException", result.errorType)
                assertEquals(expected, result.errorCode)
                assertFalse(result.toString().contains("private"))
            }
        }
    }

    @Test
    fun unavailableAuthenticationIsNotReportedAsWalletInteroperabilityFailure() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "status" -> status(false, "UNDEFINED")
                "stop" -> ""
                session -> report.replace("<result>SUCCESS</result>", "<result>UNDEFINED</result>")
                else -> error("Unexpected request")
            })
        }).use { client ->
            val result = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), Bridge(), {
                throw ItbAuthenticationUnavailable()
            }).run(suite, case)
            assertEquals(ItbCaseResult.Outcome.AUTH_UNAVAILABLE, result.outcome)
            assertEquals(ItbCaseResult.Phase.WALLET, result.phase)
            assertFalse(result.walletSucceeded)
            assertEquals(ItbSessionReport.Verdict.UNDEFINED, result.testBedVerdict)
        }
    }

    @Test
    fun portalStepTimeoutNamesOnlyItsStepAndNeverInvokesTheWallet() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "status" -> status(false, "UNDEFINED")
                "stop" -> ""
                session -> report.replace("<result>SUCCESS</result>", "<result>UNDEFINED</result>")
                else -> error("Unexpected request")
            })
        }).use { client ->
            val bridge = Bridge().apply {
                readFailure = ItbPortalStepTimeout(ItbPortalStepTimeout.Step.DOWNLOAD_EVENT)
            }
            val result = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge, {
                error("The wallet must not run")
            }).run(suite, case)
            assertEquals(ItbCaseResult.Outcome.TIMED_OUT, result.outcome)
            assertEquals("download_event", result.errorCode)
            assertEquals(ItbCaseResult.Phase.INTERACTION, result.phase)
            assertFalse(result.adapterInvoked)
        }
    }

    @Test
    fun reportForAnotherCaseCannotProduceAPass() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "status" -> status(true)
                session -> report.replace("id=\"tc_vci_006\"", "id=\"tc_vci_007\"")
                else -> error("Unexpected request")
            }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val runner = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), Bridge(), {})
            val result = runner.run(suite, case)
            assertEquals(ItbCaseResult.Outcome.ERROR, result.outcome)
            assertNull(result.testBedVerdict)
            assertTrue(result.cleanupFailed)
        }
    }

    @Test
    fun terminalNonSuccessCannotPassEvenWhenWalletTransmissionSucceeded() = runBlocking<Unit> {
        for ((verdict, expected) in listOf(
            "FAILURE" to ItbCaseResult.Outcome.ITB_FAILED,
            "UNDEFINED" to ItbCaseResult.Outcome.INCOMPLETE,
        )) {
            HttpClient(MockEngine { request ->
                respond(when (request.url.encodedPath.substringAfterLast('/')) {
                    "status" -> status(true, verdict)
                    session -> report.replace("<result>SUCCESS</result>", "<result>$verdict</result>")
                    else -> error("Unexpected request")
                })
            }).use { client ->
                val result = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), Bridge(), {})
                    .run(suite, case)
                assertEquals(expected, result.outcome)
                assertTrue(result.walletSucceeded && result.testBedCompleted)
            }
        }
    }

    @Test
    fun timeoutStopsItsSessionAndRemainsTimedOut() = runBlocking<Unit> {
        var stopped = false
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "status" -> status(false, "UNDEFINED")
                "stop" -> { stopped = true; "" }
                session -> report.replace("<result>SUCCESS</result>", "<result>UNDEFINED</result>")
                else -> error("Unexpected request")
            })
        }).use { client ->
            val result = ItbCaseRunner(
                ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), Bridge(),
                { kotlinx.coroutines.delay(Long.MAX_VALUE) }, caseTimeoutMillis = 100,
            ).run(suite, case)
            assertEquals(ItbCaseResult.Outcome.TIMED_OUT, result.outcome)
            assertTrue(stopped)
            assertFalse(result.walletSucceeded)
            assertEquals(ItbSessionReport.Verdict.UNDEFINED, result.testBedVerdict)
        }
    }

    @Test
    fun walletRejectionRemainsFailedEvenWhenTheIssuerSideReportIsSuccessful() = runBlocking<Unit> {
        val bridge = Bridge()
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "status" -> status(true)
                session -> report
                else -> error("A terminal session must not be stopped")
            })
        }).use { client ->
            val result = ItbCaseRunner(
                ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge,
                { throw IllegalArgumentException("credential parsing failed") },
            ).run(suite, case)
            assertEquals(ItbCaseResult.Outcome.WALLET_FAILED, result.outcome)
            assertEquals(ItbSessionReport.Verdict.SUCCESS, result.testBedVerdict)
            assertTrue(result.testBedCompleted)
            assertFalse(result.walletSucceeded)
            assertFalse(bridge.completed)
            assertFalse(result.cleanupFailed)
        }
    }
    @Test
    fun failedPreparationIsNotRetriedAndCannotStopUnownedSessions() = runBlocking<Unit> {
        var preparations = 0
        val bridge = object : ItbInteractionBridge {
            override suspend fun prepare(suite: ItbCatalogue.Suite, case: ItbCatalogue.Case): ItbSession {
                preparations++
                throw IllegalStateException("private portal details")
            }
            override suspend fun read(session: ItbSession): ItbWalletInteraction = error("Must not start")
            override suspend fun complete() = error("Must not complete")
        }
        HttpClient(MockEngine { error("No owned session exists; do not query or stop tenant sessions") }).use { client ->
            val result = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge, {
                error("Wallet must not run")
            }).run(suite, case)
            assertEquals(1, preparations)
            assertEquals(ItbCaseResult.Outcome.ERROR, result.outcome)
            assertEquals(ItbCaseResult.Phase.START, result.phase)
            assertNull(result.session)
            assertFalse(result.adapterInvoked || result.cleanupFailed)
            assertFalse(result.toString().contains("private portal details"))
        }
    }

    @Test
    fun failedInteractiveStartRetainsAndCleansItsPreparedSession() = runBlocking<Unit> {
        val stopped = mutableListOf<String>()
        val bridge = object : ItbInteractionBridge {
            override suspend fun prepare(suite: ItbCatalogue.Suite, case: ItbCatalogue.Case) =
                ItbSession(suite.id, case.id, session)
            override suspend fun read(session: ItbSession): ItbWalletInteraction =
                throw ItbPortalStepTimeout(ItbPortalStepTimeout.Step.START)
            override suspend fun complete() = error("Must not complete")
        }
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "status" -> status(false, "UNDEFINED")
                "stop" -> {
                    stopped += (request.body as io.ktor.http.content.TextContent).text
                    ""
                }
                session -> report.replace("<result>SUCCESS</result>", "<result>UNDEFINED</result>")
                else -> error("Unexpected request")
            })
        }).use { client ->
            val result = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge, {
                error("Wallet must not run")
            }).run(suite, case)
            assertEquals(listOf("""{"session":["$session"]}"""), stopped)
            assertEquals(session, result.session)
            assertEquals(ItbCaseResult.Outcome.TIMED_OUT, result.outcome)
            assertEquals("start", result.errorCode)
            assertEquals(ItbCaseResult.Phase.INTERACTION, result.phase)
            assertFalse(result.adapterInvoked || result.cleanupFailed)
            assertTrue(result.testBedCompleted)
            assertEquals(ItbSessionReport.Verdict.UNDEFINED, result.testBedVerdict)
        }
    }

}
