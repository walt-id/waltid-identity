package id.walt.openid4vp.conformance.testplans.http

import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestResponse
import id.walt.openid4vp.conformance.testplans.httpdata.TestRunInfo
import id.walt.openid4vp.conformance.testplans.httpdata.TestRunResult
import id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner.Companion.baseUrlBuilderSetup
import id.walt.openid4vp.conformance.utils.JsonUtils.fromJson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

class ConformanceInterface(
  //  val conformanceHttp: HttpClient
) {

    companion object {
        val conformanceHttp = HttpClient(OkHttp) {
            followRedirects = false

            defaultRequest {
                url {
                    baseUrlBuilderSetup()
                }
            }
            install(ContentNegotiation) {
                json()
            }
            install(Logging) {
                level = LogLevel.ALL
            }
        }
    }

    // Conformance
    suspend fun getServerVersion() =
        conformanceHttp.get("/api/server")
            .body<JsonObject>()["version"]?.jsonPrimitive?.content

    fun createTestPlanUrl(testPlanCreationUrl: ParametersBuilder.() -> Unit) =
        URLBuilder("/api/plan").apply {
            baseUrlBuilderSetup()
            parameters.apply {
                testPlanCreationUrl.invoke(this)
            }
        }.build()

    suspend fun createTestPlan(
        createTestPlanUrl: Url,
        testPlanCreationConfiguration: JsonObject
    ): CreateTestPlanResponse =
        conformanceHttp.post(createTestPlanUrl) {
            contentType(ContentType.Application.Json)
            setBody(testPlanCreationConfiguration)
        }.bodyAsText().also { println(it) }.fromJson<CreateTestPlanResponse>()

    fun buildTestUrl(testPlanId: String, testModule: String) =
        URLBuilder("/api/runner").apply {
            baseUrlBuilderSetup()
            parameters.apply {
                append("test", testModule)
                append("plan", testPlanId)
                append("variant", "{}")
            }
        }.build()

    suspend fun createTest(createTestUrl: Url): CreateTestResponse =
        conformanceHttp.post(createTestUrl).body<CreateTestResponse>()

    suspend fun getTestRun(testId: String): TestRunResult =
        conformanceHttp.get("/api/runner/$testId").body<TestRunResult>()

    suspend fun getTestRunInfo(testId: String) =
        conformanceHttp.get("/api/info/$testId").body<TestRunInfo>()


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


            if (counter > 10) {
                throw IllegalStateException("Waited for 10 tries, but test is still not ready for presentation")
            }

            delay(1.seconds)
        }
    }

}
