package extensions.service

import id.walt.commons.web.WebService
import id.walt.verifier.getService
import id.walt.verifier.verifierModule
import io.ktor.server.application.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtensionContext

class VerifierServiceExtension : BaseServiceExtension() {
    override fun beforeAll(context: ExtensionContext?) = runTest {
        super.beforeAll(context)
        getService(WebService(Application::verifierModule).run()).main(arrayOf("-l", "trace"))
    }
}