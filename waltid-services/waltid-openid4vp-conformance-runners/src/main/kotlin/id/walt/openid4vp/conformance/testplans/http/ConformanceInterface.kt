package id.walt.openid4vp.conformance.testplans.http

import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestResponse
import id.walt.openid4vp.conformance.testplans.httpdata.TestRunInfo
import id.walt.openid4vp.conformance.testplans.httpdata.TestRunResult
import id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner.Companion.baseUrlBuilderSetup
import id.walt.openid4vp.conformance.utils.JsonUtils.fromJson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
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
) {

    val conformanceHttp = HttpClient(OkHttp) {
        followRedirects = false

        defaultRequest {
            url {
                baseUrlBuilderSetup(conformanceHost, conformancePort)
            }
        }
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    /** Get conformance suite version (mostly for healthcheck) */
    suspend fun getServerVersion() =
        conformanceHttp.get("/api/server")
            .body<JsonObject>()["version"]?.jsonPrimitive?.content

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
    ): CreateTestPlanResponse =
        conformanceHttp.post(createTestPlanUrl) {
            contentType(ContentType.Application.Json)
            setBody(testPlanCreationConfiguration)
        }.bodyAsText().also { println(it) }.fromJson<CreateTestPlanResponse>()

    /**
     * To create a test, some parameters already have to be put into the URL
     * This method allows for creation of said URL to create a test.
     */
    fun buildCreateTestUrl(testPlanId: String, testModule: String) =
        URLBuilder("/api/runner").apply {
            baseUrlBuilderSetup(conformanceHost, conformancePort)
            parameters.apply {
                append("test", testModule)
                append("plan", testPlanId)
                append("variant", "{}")
            }
        }.build()

    /**
     * Create a test with configuration URL created with [buildCreateTestUrl] supplied in [createTestUrl]
     */
    suspend fun createTest(createTestUrl: Url): CreateTestResponse =
        conformanceHttp.post(createTestUrl).body<CreateTestResponse>()

    /** Get [TestRunResult] for a test referenced by [testId] */
    suspend fun getTestRun(testId: String): TestRunResult =
        conformanceHttp.get("/api/runner/$testId").body<TestRunResult>()

    /** Get [TestRunInfo] for a test referenced by [testId] */
    suspend fun getTestRunInfo(testId: String): TestRunInfo =
        conformanceHttp.get("/api/info/$testId").body<TestRunInfo>()


    /**
     * Wait ([delay]) until the test referenced by [testId] reached the defined status
     * - [shouldBeWaiting] = true: wait until test is in "waiting" state
     * - [shouldBeWaiting] = false: wait until test is no longer in "waiting" state
     */
    suspend fun waitForTestStatus(testId: String, shouldBeWaiting: Boolean) {
        var counter = 0
        while (true) {
            counter++

            val testRunInfo = getTestRunInfo(testId)
            println("Current conformance test status: ${testRunInfo.status}")


            if (shouldBeWaiting) {
                if (testRunInfo.status == "WAITING") {
                    break
                }
            } else {
                if (testRunInfo.status != "WAITING") {
                    break
                }
            }


            if (counter > 15) {
                throw IllegalStateException("Waited for ${counter - 1} tries, but test is still not ready for presentation (waiting for waiting=$shouldBeWaiting)")
            }

            delay(1.seconds)
        }
    }

}
