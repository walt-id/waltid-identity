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
import io.ktor.server.application.*
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object E2ETest {

    private var HOST = "localhost"
    private var PORT = 22222
    var failEarly = false

    data class TestStats(
        val overall: Int,
        val success: Int,
        val failed: Int,
    )

    var numTests = 0
    val testResults = ArrayList<Result<Any?>>()
    val testNames = HashMap<Int, String>()
    val t = Terminal(ansiLevel = AnsiLevel.TRUECOLOR)

    fun getBaseURL() = "http://$HOST:$PORT"

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
        port: Int = PORT,
        timeout: Duration = 5.minutes,
        block: suspend () -> Unit,
    ) =
        testBlock(
            service = ServiceMain(
                config = config,
                init = ServiceInitialization(
                    features = features,
                    featureAmendments = featureAmendments,
                    init = init,
                    run = E2ETestWebService(module).runService(block, host, port)
                )
            ),
            timeout = timeout, host = host, port = port
        )


    fun testBlock(service: ServiceMain, timeout: Duration, host: String, port: Int) = runTest(timeout = timeout) {
        HOST = host
        PORT = port

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
            result.getOrThrow()
            t.println()
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
