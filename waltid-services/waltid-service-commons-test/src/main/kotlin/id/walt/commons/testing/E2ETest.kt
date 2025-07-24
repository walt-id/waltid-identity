package id.walt.commons.testing

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.AbstractFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class E2ETest(
    val host: String = "localhost",
    val port: Int = 22222,
    val failEarly: Boolean = false,
) {
    data class TestStats(
        val overall: Int,
        val success: Int,
        val failed: Int,
    )

    private val e2eHost = this.host
    private val e2ePort = this.port

    fun testHttpClient(bearerToken: String? = null, doFollowRedirects: Boolean = true, block: HttpClientConfig<*>.() -> Unit = {}) = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
            host = e2eHost
            port = e2ePort

            if (bearerToken != null) bearerAuth(bearerToken)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        followRedirects = doFollowRedirects
        block.invoke(this)
    }

    var numTests = 0
    val testResults = ArrayList<Result<Any?>>()
    val testNames = HashMap<Int, String>()
    val t = Terminal(ansiLevel = AnsiLevel.TRUECOLOR)

    fun getBaseURL() = "http://$host:$port"

    fun getTestStats(): TestStats {
        val succeeded = testResults.count { it.isSuccess }
        val failed = testResults.size - succeeded
        return TestStats(testResults.size, succeeded, failed)
    }

    fun testBlock(
        config: ServiceConfiguration = ServiceConfiguration("e2e-test"),
        features: List<ServiceFeatureCatalog>,
        featureAmendments: Map<AbstractFeature, suspend () -> Unit> = emptyMap(),
        init: suspend () -> Unit,
        module: Application.() -> Unit,
        host: String = "localhost",
        port: Int = this.port,
        timeout: Duration = 5.minutes,
        block: suspend E2ETest.() -> Unit,
    ) = testBlock(
        service = ServiceMain(
            config = config,
            init = ServiceInitialization(
                features = features,
                featureAmendments = featureAmendments,
                init = init,
                run = E2ETestWebService(module).runService(suspend { block.invoke(this) }, host, port)
            )
        ),
        timeout = timeout
    )


    fun testBlock(service: ServiceMain, timeout: Duration) = runTest(timeout = timeout) {
        /*ServiceMain(
            ServiceConfiguration("e2e-test"), ServiceInitialization(
                features = listOf(id.walt.issuer.FeatureCatalog, id.walt.verifier.FeatureCatalog, id.walt.webwallet.FeatureCatalog),
                featureAmendments = mapOf(
                    CommonsFeatureCatalog.authenticationServiceFeature to id.walt.webwallet.web.plugins.walletAuthenticationPluginAmendment,
//                    CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
                ),
                init = {
                    id.walt.webwallet.webWalletSetup()
                    id.walt.did.helpers.WaltidServices.minimalInit()
                    id.walt.webwallet.db.Db.start()
                },
                run = E2ETestWebService(Application::e2eTestModule).run(block)
            )
        )*/
        service.main(arrayOf("-l", "trace"))

        t.println("\n" + TextColors.magenta("Test results:"))
        testResults.forEachIndexed { index, result ->
            val name = testNames[index]!!
            t.println(TextColors.magenta("$index. $name: ${result.toSuccessString()}"))
        }

        val testStats = getTestStats()
        if (testStats.failed > 0) {
            error("${testStats.failed} tests failed!")
        }

        if (testStats.overall == 0) {
            error("Error - no E2E tests were executed!")
        }
    }

    fun Result<*>.toSuccessString() = if (isSuccess) {
        val res = if (getOrNull() !is Unit) " (${getOrNull().toString()})" else ""
        TextColors.green("✅ SUCCESS$res")
    } else {
        val res = exceptionOrNull()!!.message?.let { " ($it)" } ?: ""
        TextColors.red("❌ FAILURE$res")
    }

    suspend fun test(name: String, function: suspend () -> Any?) {
        val id = numTests++
        testNames[id] = name

        t.println("\n${TextColors.cyan(TextStyles.bold("---=== Start $id. test: $name === ---"))}")

        val result = runCatching { function.invoke() }
        if (failEarly && result.isFailure) {
            t.println("\n${TextColors.brightRed("Fail early called for $id. test: $name")}")
            val err = result.exceptionOrNull()!!
            t.println("Error causing fail early: ${err.stackTraceToString()}")
            t.println()
            result.getOrThrow()
        }

        testResults.add(result)

        t.println(TextColors.blue("End result of test \"$name\": $result"))


        if (result.isFailure) {
            result.exceptionOrNull()!!.printStackTrace()
        }

        t.println(TextStyles.bold(TextColors.cyan("---===  End  ${id}. test: $name === ---") + " " + result.toSuccessString()) + "\n")

        val overallSuccess = testResults.count { it.isSuccess }
        val failed = testResults.size - overallSuccess
        val failedStr = if (failed == 0) "none failed ✅" else TextColors.red("$failed failed")
        t.println(TextColors.magenta("Current test stats: ${testResults.size} overall | $overallSuccess succeeded | $failedStr\n"))
    }

}
