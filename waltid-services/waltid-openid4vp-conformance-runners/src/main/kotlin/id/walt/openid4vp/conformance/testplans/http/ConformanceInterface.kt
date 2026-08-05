package id.walt.openid4vp.conformance.testplans.http

import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestResponse
import id.walt.openid4vp.conformance.testplans.httpdata.TestRunInfo
import id.walt.openid4vp.conformance.testplans.httpdata.TestRunResult
import id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner.Companion.baseUrlBuilderSetup
import id.walt.openid4vp.conformance.utils.JsonUtils.fromJson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
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

    // Use simple HttpClient - relies on javax.net.ssl.trustStore system property
    // set in build.gradle.kts for SSL certificate trust
    val conformanceHttp = HttpClient() {
        followRedirects = false

        defaultRequest {
            url {
                baseUrlBuilderSetup(conformanceHost, conformancePort)
            }
        }
        install(ContentNegotiation) {
            json()
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

    /** Builds a test-plan URL with the suite-defined creation parameters. */
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
        val response = conformanceHttp.post(createTestPlanUrl) {
            contentType(ContentType.Application.Json)
            setBody(testPlanCreationConfiguration)
        }
        val responseBody = response.bodyAsText()
        check(response.status.isSuccess()) {
            "Conformance suite returned ${response.status} while creating a test plan: ${responseBody.take(1_000)}"
        }

        if (responseBody.contains("\"error\"")) {
            throw IllegalStateException("Conformance suite error: ${responseBody.take(1_000)}")
        }

        return responseBody.fromJson<CreateTestPlanResponse>()
    }

    /** Builds a test-instance URL with the selected module variant. */
    fun buildCreateTestUrl(
        testPlanId: String,
        testModule: String,
        variant: JsonObject = JsonObject(emptyMap()),
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
        val response = conformanceHttp.post(createTestUrl)
        val responseBody = response.bodyAsText()
        check(response.status.isSuccess()) {
            "Conformance suite returned ${response.status} while creating a test: ${responseBody.take(1_000)}"
        }

        if (responseBody.contains("\"error\"")) {
            throw IllegalStateException("Conformance suite error: ${responseBody.take(1_000)}")
        }

        return responseBody.fromJson<CreateTestResponse>()
    }

    /** Get [TestRunResult] for a test referenced by [testId] */
    suspend fun getTestRun(testId: String): TestRunResult =
        conformanceHttp.get("/api/runner/$testId").body<TestRunResult>()

    /** Stop a test that cannot progress because the local adapter has already failed. */
    suspend fun cancelTest(testId: String) {
        val response = conformanceHttp.delete("/api/runner/$testId")
        check(response.status.value in 200..299) {
            "Conformance suite returned ${response.status} while cancelling test $testId"
        }
    }

    /** Mark a front-channel browser URL as visited, matching the conformance-suite UI behavior. */
    suspend fun markBrowserUrlVisited(testId: String, url: String) {
        val response = conformanceHttp.post("/api/runner/browser/$testId/visit") {
            parameter("url", url)
        }
        check(response.status.value in 200..299) {
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
        check(response.status.value in 200..299) {
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


    /**
     * Wait ([delay]) until the test referenced by [testId] reached the defined status
     * - [shouldBeWaiting] = true: wait until test is in "waiting" state
     * - [shouldBeWaiting] = false: wait until test is no longer in "waiting" state
     */
    suspend fun waitForTestStatus(testId: String, shouldBeWaiting: Boolean) {
        var counter = 0
        // Give conformance suite time to initialize the test
        delay(2.seconds)

        while (true) {
            counter++

            val testRunInfo = getTestRunInfo(testId)
            println("Current conformance test status: ${testRunInfo.status}")


            val expectedStatusReached = if (shouldBeWaiting) {
                testRunInfo.status == "WAITING"
            } else {
                testRunInfo.status != "WAITING"
            }
            if (expectedStatusReached) break

            if (counter > 30) {
                throw IllegalStateException(
                    "Waited for ${counter - 1} tries, but test is still not ready " +
                        "for presentation (waiting=$shouldBeWaiting)"
                )
            }

            delay(1.seconds)
        }
    }

    override fun close() {
        conformanceHttp.close()
    }
}
