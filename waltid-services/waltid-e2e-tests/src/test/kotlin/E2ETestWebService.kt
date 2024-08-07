import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.ServiceMain
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.commons.web.plugins.configureSerialization
import id.walt.commons.web.plugins.configureStatusPages
import id.walt.credentials.verification.PolicyManager
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.FeatureCatalog
import id.walt.issuer.issuerModule
import id.walt.webwallet.web.plugins.walletAuthenticationPluginAmendment
import id.walt.issuer.lspPotential.lspPotentialIssuanceTestApi
import id.walt.verifier.lspPotential.lspPotentialVerificationTestApi
import id.walt.verifier.policies.PresentationDefinitionPolicy
import id.walt.verifier.verifierModule
import id.walt.webwallet.db.Db
import id.walt.webwallet.webWalletModule
import id.walt.webwallet.webWalletSetup
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.io.File
import java.net.URLDecoder

object E2ETestWebService {

    data class TestWebService(
        val module: Application.() -> Unit,
    ) {
        private val webServiceModule: Application.() -> Unit = {
            configureStatusPages()
            configureSerialization()
            AuthenticationServiceModule.run { enable() }
            module.invoke(this)
        }

        fun run(block: suspend () -> Unit): suspend () -> Unit = {
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

    var numTests = 0
    val testResults = ArrayList<Result<Any?>>()
    val testNames = HashMap<Int, String>()
    val t = Terminal(ansiLevel = AnsiLevel.TRUECOLOR)

    suspend fun testBlock(block: suspend () -> Unit) {

        fun getTestStats(): TestStats {
            val succeeded = testResults.count { it.isSuccess }
            val failed = testResults.size - succeeded
            return TestStats(testResults.size, succeeded, failed)
        }


        ServiceMain(
            ServiceConfiguration("e2e-test"), ServiceInitialization(
                features = listOf(FeatureCatalog, id.walt.verifier.FeatureCatalog, id.walt.webwallet.FeatureCatalog),
                featureAmendments = mapOf(
                    CommonsFeatureCatalog.authenticationServiceFeature to walletAuthenticationPluginAmendment,
//                    CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
                ),
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

    fun loadResource(relativePath: String): String =
        URLDecoder.decode(object {}.javaClass.getResource(relativePath)!!.path, "UTF-8").let { File(it).readText() }
}

typealias TestFunctionType = (String, suspend() -> Any?) -> Unit

private fun Application.e2eTestModule() {
    webWalletModule(true)
    issuerModule(false)
    lspPotentialIssuanceTestApi()
    verifierModule(false)
    lspPotentialVerificationTestApi()
}
