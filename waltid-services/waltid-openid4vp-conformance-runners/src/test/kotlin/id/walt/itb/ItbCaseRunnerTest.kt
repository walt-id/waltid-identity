package id.walt.itb

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ItbCaseRunnerTest {
    private val session = "00000000-0000-0000-0000-000000000001"
    private val report = javaClass.getResource("/itb/vci006-success.xml")!!.readText()
    private fun status(complete: Boolean, verdict: String = "SUCCESS") =
        """{"sessions":[{"session":"$session","result":"$verdict","startTime":"2026-09-21T14:48:14Z"${if (complete) ",\"endTime\":\"2026-09-21T14:49:58Z\"" else ""}}]}"""

    private class Bridge : ItbInteractionBridge {
        var reads = 0
        var completed = false
        override suspend fun read(session: ItbRestClient.CreatedSession): ItbWalletInteraction {
            reads++
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
                "start" -> """{"createdSessions":[{"testSuite":"cs01v1","testCase":"tc_vci_006","session":"$session"}]}"""
                "status" -> status(++statusCalls >= 2)
                session -> report
                else -> error("Unexpected request")
            }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val runner = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge, { walletCalls++ }, pollMillis = 1)
            val result = runner.run("system", "actor", "cs01v1", "tc_vci_006")
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
                "start" -> """{"createdSessions":[{"testSuite":"cs01v1","testCase":"tc_vci_006","session":"$session"}]}"""
                "status" -> status(false, "UNDEFINED")
                "stop" -> { stopped = true; "" }
                session -> report.replace("<result>SUCCESS</result>", "<result>UNDEFINED</result>")
                else -> error("Unexpected request")
            }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val runner = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge, {
                throw IllegalStateException("sensitive protocol payload")
            })
            val result = runner.run("system", "actor", "cs01v1", "tc_vci_006")
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
    fun reportForAnotherCaseCannotProduceAPass() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath.substringAfterLast('/')) {
                "start" -> """{"createdSessions":[{"testSuite":"cs01v1","testCase":"tc_vci_006","session":"$session"}]}"""
                "status" -> status(true)
                session -> report.replace("id=\"tc_vci_006\"", "id=\"tc_vci_007\"")
                else -> error("Unexpected request")
            }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val runner = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), Bridge(), {})
            val result = runner.run("system", "actor", "cs01v1", "tc_vci_006")
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
                    "start" -> """{"createdSessions":[{"testSuite":"cs01v1","testCase":"tc_vci_006","session":"$session"}]}"""
                    "status" -> status(true, verdict)
                    session -> report.replace("<result>SUCCESS</result>", "<result>$verdict</result>")
                    else -> error("Unexpected request")
                })
            }).use { client ->
                val result = ItbCaseRunner(ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), Bridge(), {})
                    .run("system", "actor", "cs01v1", "tc_vci_006")
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
                "start" -> """{"createdSessions":[{"testSuite":"cs01v1","testCase":"tc_vci_006","session":"$session"}]}"""
                "status" -> status(false, "UNDEFINED")
                "stop" -> { stopped = true; "" }
                session -> report.replace("<result>SUCCESS</result>", "<result>UNDEFINED</result>")
                else -> error("Unexpected request")
            })
        }).use { client ->
            val result = ItbCaseRunner(
                ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), Bridge(),
                { kotlinx.coroutines.delay(Long.MAX_VALUE) }, caseTimeoutMillis = 100,
            ).run("system", "actor", "cs01v1", "tc_vci_006")
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
                "start" -> """{"createdSessions":[{"testSuite":"cs01v1","testCase":"tc_vci_006","session":"$session"}]}"""
                "status" -> status(true)
                session -> report
                else -> error("A terminal session must not be stopped")
            })
        }).use { client ->
            val result = ItbCaseRunner(
                ItbRestClient(client, Url("https://itb.example/api/rest"), "secret"), bridge,
                { throw IllegalArgumentException("credential parsing failed") },
            ).run("system", "actor", "cs01v1", "tc_vci_006")
            assertEquals(ItbCaseResult.Outcome.WALLET_FAILED, result.outcome)
            assertEquals(ItbSessionReport.Verdict.SUCCESS, result.testBedVerdict)
            assertTrue(result.testBedCompleted)
            assertFalse(result.walletSucceeded)
            assertFalse(bridge.completed)
            assertFalse(result.cleanupFailed)
        }
    }
}
