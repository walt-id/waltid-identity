import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.web.plugins.configureSerialization
import id.walt.commons.web.plugins.configureStatusPages
import id.walt.credentials.verification.PolicyManager
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.FeatureCatalog
import id.walt.issuer.issuerModule
import id.walt.verifier.policies.PresentationDefinitionPolicy
import id.walt.verifier.verifierModule
import id.walt.webwallet.db.Db
import id.walt.webwallet.webWalletModule
import id.walt.webwallet.webWalletSetup
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

object E2ETestWebService {

    data class TestWebService(
        val module: Application.() -> Unit,
    ) {
        private val webServiceModule: Application.() -> Unit = {
            configureStatusPages()
            configureSerialization()

            module.invoke(this)
        }

        //        fun run(block: suspend Application.() -> Unit): suspend () -> Unit = {
        fun run(block: suspend () -> Unit): suspend () -> Unit = {
            /*testApplication {
                application {
                    webServiceModule()
                }
                block.invoke(this@testApplication)
            }*/

            embeddedServer(
                CIO,
                port = 22222,
                host = "127.0.0.1",
                module = webServiceModule
            ).start(wait = false)

            block.invoke()
        }
    }

    data class TestStats(
        val overall: Int,
        val success: Int,
        val failed: Int,
    )


    val testResults = ArrayList<Result<Any?>>()
    val testNames = HashMap<Int, String>()
    val t = Terminal(ansiLevel = AnsiLevel.TRUECOLOR)

    //    suspend fun testBlock(block: suspend ApplicationTestBuilder.() -> Unit) {
    suspend fun testBlock(block: suspend () -> Unit) {

        fun getTestStats(): TestStats {
            val succeeded = testResults.count { it.isSuccess }
            val failed = testResults.size - succeeded
            return TestStats(testResults.size, succeeded, failed)
        }


        ServiceMain(
            ServiceConfiguration("e2e-test"), ServiceInitialization(
                features = listOf(FeatureCatalog, id.walt.verifier.FeatureCatalog, id.walt.webwallet.FeatureCatalog),
                init = {
                    webWalletSetup()
                    PolicyManager.registerPolicies(PresentationDefinitionPolicy())
                    WaltidServices.minimalInit()
                    Db.start()
                },
                run = TestWebService(Application::e2eTestModule).run(block)
            )
        ).main(arrayOf("-l", "trace"))

        t.println("\n" + TextColors.magenta("Test results:"))
        testResults.forEachIndexed { index, result ->
            val idx = index + 1
            val name = testNames[idx]!!
            t.println(TextColors.magenta("$idx. $name: ${result.toSuccessString()}"))
        }

        val testStats = getTestStats()
        if (testStats.failed > 0) {
            error("${testStats.failed} tests failed!")
        }

        if (testStats.overall == 0) {
            error("Error - no E2E tests were executed!")
        }
    }

    fun Result<*>.toSuccessString() = if (isSuccess)
        TextColors.green("✅ SUCCESS")
    else
        TextColors.red("❌ FAILURE")

    //    suspend fun ApplicationTestBuilder.test(name: String, function: suspend () -> Any?) {
    suspend fun test(name: String, function: suspend () -> Any?) {
        val id = testResults.size + 1
        testNames[id] = name

        t.println("\n${TextColors.cyan(TextStyles.bold("---=== Start $id. test: $name === ---"))}")

        val result = runCatching { function.invoke() }
        testResults.add(result)

        t.println(TextColors.blue("End result of test \"$name\": $result"))

        t.println(TextStyles.bold(TextColors.cyan("---===  End  ${id}. test: $name === ---") + " " + result.toSuccessString()) + "\n")

        val overallSuccess = testResults.count { it.isSuccess }
        val failed = testResults.size - overallSuccess
        val failedStr = if (failed == 0) "none failed ✅" else TextColors.red("$failed failed")
        t.println(TextColors.magenta("Current test stats: ${testResults.size} overall | $overallSuccess succeeded | $failedStr\n"))
    }
}

private fun Application.e2eTestModule() {
    webWalletModule(true)
    issuerModule(false)
    verifierModule(false)
}
