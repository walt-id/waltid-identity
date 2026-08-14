package id.walt.openid4vp.conformance.testplans.http

import id.walt.openid4vp.conformance.testplans.httpdata.*
import id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner.Companion.baseUrlBuilderSetup
import id.walt.openid4vp.conformance.utils.JsonUtils.fromJson
import id.walt.openid4vp.conformance.utils.JsonUtils.lenientJson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

class ConformanceInterface(
    val conformanceHost: String,
    val conformancePort: Int
) : AutoCloseable {

    companion object {
        /**
         * 1x1 PNG used to satisfy the suite's screenshot placeholders in automated runs.
         * See [fulfillPendingImagePlaceholders] for why synthetic evidence is appropriate there.
         */
        /**
         * Polling attempts (roughly seconds) before giving up on a status change.
         *
         * Has to exceed the 30 s the wallet test modules sleep before concluding that a wallet
         * correctly aborted on a bad request - otherwise every negative module times out here
         * instead of being recorded as the pass it is.
         */
        const val DEFAULT_WAIT_ATTEMPTS = 60

        /**
         * Conformance statuses a test never leaves.
         *
         * `INTERRUPTED` is the one that matters in practice: the suite aborts a test, and a poller
         * waiting for `WAITING` would otherwise spin out its whole budget before reporting.
         */
        val TERMINAL_STATUSES = setOf("INTERRUPTED", "FINISHED")

        private const val AUTOMATED_EVIDENCE_IMAGE =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNk" +
                    "AAIAAAoAAv/lxKUAAAAASUVORK5CYII="
    }

    // Use simple HttpClient - relies on javax.net.ssl.trustStore system property
    // set in build.gradle.kts for SSL certificate trust
    val conformanceHttp = HttpClient {
        followRedirects = false

        defaultRequest {
            url {
                baseUrlBuilderSetup(conformanceHost, conformancePort)
            }
        }
        install(ContentNegotiation) {
            // Tolerate response fields added by newer conformance-suite releases
            json(lenientJson)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
        }
    }

    /** Get conformance suite version (mostly for healthcheck) */
    suspend fun getServerVersion() =
        conformanceHttp.get("/api/server")
            .body<JsonObject>()["version"]?.jsonPrimitive?.content

    /**
     * Every test module the suite publishes, keyed by module name, with its variant metadata.
     *
     * Used to tell whether a module a plan published actually applies to that plan's variant; the
     * suite does not always get this right on its own. See
     * [id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletModuleApplicability].
     *
     * The response covers every module of every profile (several MB), so callers should fetch it once
     * per run rather than per module.
     */
    suspend fun getAvailableTestModules(): Map<String, AvailableTestModule> =
        conformanceHttp.get("/api/runner/available")
            .body<List<AvailableTestModule>>()
            .mapNotNull { module -> module.testName?.let { it to module } }
            .toMap()

    /**
     * To create a test plan, some parameters already have to be put into the URL
     * This method allows for creation of said URL.
     */
    fun createTestPlanUrlWithConfig(testPlanCreationUrl: ParametersBuilder.() -> Unit) =
        URLBuilder("/api/plan").apply {
            baseUrlBuilderSetup(conformanceHost, conformancePort)
            parameters.apply {
                testPlanCreationUrl.invoke(this)
            }
        }.build()

    /**
     * Create test plan with the configuration supplied in [testPlanCreationConfiguration]
     * and the URL of the [createTestPlanUrlWithConfig] function supplied in [createTestPlanUrl]
     * This method allows for creation of said URL.
     */
    suspend fun createTestPlan(
        createTestPlanUrl: Url,
        testPlanCreationConfiguration: JsonObject
    ): CreateTestPlanResponse {
        println("POST body: $testPlanCreationConfiguration")
        val response = conformanceHttp.post(createTestPlanUrl) {
            contentType(ContentType.Application.Json)
            setBody(testPlanCreationConfiguration)
        }.bodyAsText().also { println(it) }

        // Check if response is an error
        if (response.contains("\"error\"")) {
            throw IllegalStateException("Conformance suite error: $response")
        }

        return response.fromJson<CreateTestPlanResponse>()
    }

    /**
     * To create a test, some parameters already have to be put into the URL
     * This method allows for creation of said URL to create a test.
     */
    fun buildCreateTestUrl(
        testPlanId: String,
        testModule: String,
        variant: JsonObject = JsonObject(emptyMap())
    ) =
        URLBuilder("/api/runner").apply {
            baseUrlBuilderSetup(conformanceHost, conformancePort)
            parameters.apply {
                append("test", testModule)
                append("plan", testPlanId)
                append("variant", variant.toString())
            }
        }.build()

    /**
     * Create a test with configuration URL created with [buildCreateTestUrl] supplied in [createTestUrl]
     */
    suspend fun createTest(createTestUrl: Url): CreateTestResponse {
        val response = conformanceHttp.post(createTestUrl).bodyAsText().also { println(it) }

        if (response.contains("\"error\"")) {
            throw IllegalStateException("Conformance suite error: $response")
        }

        return response.fromJson<CreateTestResponse>()
    }

    /** Get [TestRunResult] for a test referenced by [testId] */
    suspend fun getTestRun(testId: String): TestRunResult =
        conformanceHttp.get("/api/runner/$testId").body<TestRunResult>()

    /** Mark a front-channel browser URL as visited, matching the conformance-suite UI behavior. */
    suspend fun markBrowserUrlVisited(testId: String, url: String) {
        val response = conformanceHttp.post("/api/runner/browser/$testId/visit") {
            parameter("url", url)
        }
        check(!(response.status.value !in 200..299)) {
            "Conformance suite returned ${response.status} while marking browser URL as visited"
        }
    }

    /** Deliver an issuer-initiated credential offer to the endpoint exposed by the conformance suite. */
    suspend fun deliverCredentialOffer(
        credentialOfferEndpoint: String,
        parameterName: String,
        parameterValue: String,
    ) {
        val url = URLBuilder(credentialOfferEndpoint).apply {
            parameters.append(parameterName, parameterValue)
        }.build()

        val response = conformanceHttp.get(url)
        check(!(response.status.value !in 200..299)) {
            "Conformance suite returned ${response.status} while delivering credential offer"
        }
    }

    /** Get [TestRunInfo] for a test referenced by [testId] */
    suspend fun getTestRunInfo(testId: String): TestRunInfo {
        val response = conformanceHttp.get("/api/info/$testId") {
            header(HttpHeaders.CacheControl, "no-cache")
        }
        return response.body<TestRunInfo>()
    }

    /** Get the full test log for a test referenced by [testId] */
    suspend fun getTestLog(testId: String): List<TestLogEntry> =
        conformanceHttp.get("/api/log/$testId") {
            header(HttpHeaders.CacheControl, "no-cache")
        }.body<List<TestLogEntry>>()

    /**
     * Fill the image placeholder [placeholder] of test [testId] with [imageDataUri].
     *
     * Mirrors what the conformance-suite UI does when a tester uploads a screenshot.
     */
    suspend fun uploadImagePlaceholder(testId: String, placeholder: String, imageDataUri: String) {
        val response = conformanceHttp.post("/api/log/$testId/images/$placeholder") {
            contentType(ContentType.Text.Plain)
            setBody(imageDataUri)
        }
        check(response.status.isSuccess()) {
            "Conformance suite returned ${response.status} while uploading image placeholder $placeholder"
        }
    }

    /**
     * Fill every outstanding image placeholder of test [testId] and return how many were filled.
     *
     * Since conformance-suite release-v5.2.2 the positive OpenID4VP verifier modules stop in
     * `WAITING` after a successful `direct_post` and ask for a screenshot of the verifier's
     * verification result, because deferred verification means the suite cannot decide over HTTP
     * whether the VP Token was accepted. Supplying the evidence is what lets the test finish (with
     * result `REVIEW`); without it an otherwise perfect run just times out.
     *
     * The evidence is necessarily synthetic in an automated run - there is no verifier UI to
     * capture. The conformance suite's own CI automation does the same thing: it screenshots the
     * suite-served `verification-evidence` page, which is itself labelled "Automated stand-in for
     * the verifier's verification-result screenshot". Certification runs still require a human to
     * upload a real screenshot.
     */
    suspend fun fulfillPendingImagePlaceholders(testId: String): Int =
        getTestLog(testId)
            .filter { it.isPendingUpload }
            .onEach { entry ->
                println("Filling image placeholder '${entry.upload}' for ${entry.src} (${entry.requirements.joinToString()})")
                uploadImagePlaceholder(testId, entry.upload!!, AUTOMATED_EVIDENCE_IMAGE)
            }
            .size


    /**
     * Cancel the test referenced by [testId] (`DELETE /api/runner/{id}`).
     *
     * Every module of a plan shares the plan's `alias`, and the suite only lets one test hold an
     * alias at a time: creating the next module's test kills any predecessor still running, replacing
     * its real outcome with "Stopping test due to alias conflict". Cancelling deliberately once a
     * module is done keeps each module's recorded result its own.
     *
     * Best effort - a test that already finished is simply not running any more (404).
     */
    suspend fun cancelTest(testId: String) {
        val response = conformanceHttp.delete("/api/runner/$testId")
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) {
            println("Could not cancel test $testId: HTTP ${response.status.value}")
        }
    }

    /**
     * Wait ([delay]) until the test referenced by [testId] reached the defined status
     * - [shouldBeWaiting] = true: wait until test is in "waiting" state
     * - [shouldBeWaiting] = false: wait until test is no longer in "waiting" state
     *
     * With [fulfillImagePlaceholders] set, any outstanding screenshot placeholder is filled while
     * waiting - otherwise a module that ends in manual review would never leave "waiting".
     * See [fulfillPendingImagePlaceholders].
     */
    suspend fun waitForTestStatus(
        testId: String,
        shouldBeWaiting: Boolean,
        fulfillImagePlaceholders: Boolean = false,
        maxAttempts: Int = DEFAULT_WAIT_ATTEMPTS,
    ) {
        var counter = 0
        // Give conformance suite time to initialize the test
        delay(2.seconds)

        while (true) {
            counter++

            val testRunInfo = getTestRunInfo(testId)
            println("Current conformance test status: ${testRunInfo.status}")


            if (shouldBeWaiting) {
                if (testRunInfo.status == "WAITING") {
                    break
                }
                // A test that has already reached a terminal state will never become ready, so
                // spinning out the attempt budget only delays the report by a minute per module and
                // then blames the wrong thing ("not ready for presentation"). Fail immediately and
                // name the status actually reached.
                if (testRunInfo.status in TERMINAL_STATUSES) {
                    throw IllegalStateException(
                        "Test $testId reached terminal status ${testRunInfo.status} before it was " +
                                "ready for presentation"
                    )
                }
            } else {
                if (testRunInfo.status != "WAITING") {
                    break
                }
                if (fulfillImagePlaceholders) {
                    fulfillPendingImagePlaceholders(testId)
                }
            }


            if (counter > maxAttempts) {
                // Name the transition that did not happen. Reporting "not ready for presentation" for
                // both directions blamed the wrong half of the flow for every module that had already
                // been presented to and was only waiting for the suite to reach a verdict.
                val awaited = if (shouldBeWaiting) {
                    "become ready for presentation (status WAITING)"
                } else {
                    "leave WAITING after being presented to"
                }
                throw IllegalStateException(
                    "Waited for ${counter - 1} tries, but test $testId did not $awaited " +
                            "(last status: ${getTestRunInfo(testId).status})"
                )
            }

            delay(1.seconds)
        }
    }

    override fun close() {
        conformanceHttp.close()
    }
}
