import com.github.ajalt.mordant.rendering.AnsiLevel
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
import java.io.File
import java.net.URLDecoder

object E2ETestWebService {

    data class TestWebService(
        val module: Application.() -> Unit,
    ) {
        private val webServiceModule: Application.() -> Unit = {
            configureStatusPages()
            configureSerialization()

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


    val testResults = ArrayList<Result<Any?>>()
    val testNames = HashMap<Int, String>()
    val t = Terminal(ansiLevel = AnsiLevel.TRUECOLOR)

    suspend fun testBlock(block: suspend () -> Unit) {

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
        //printStats
    }

    suspend fun test(name: String, function: suspend () -> Any?) {
        val id = testResults.size + 1
        testNames[id] = name

        val result = runCatching { function.invoke() }
        //logTestResult
    }

    fun loadResource(relativePath: String): String =
        URLDecoder.decode(this.javaClass.getResource(relativePath)!!.path, "UTF-8").let { File(it).readText() }
}

private fun Application.e2eTestModule() {
    webWalletModule(true)
    issuerModule(false)
    verifierModule(false)
}
