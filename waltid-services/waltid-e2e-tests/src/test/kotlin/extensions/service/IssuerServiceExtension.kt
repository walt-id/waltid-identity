package extensions.service

import id.walt.commons.web.WebService
import id.walt.issuer.getService
import id.walt.issuer.issuerModule
import io.ktor.server.application.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtensionContext

class IssuerServiceExtension : BaseServiceExtension() {
    override fun beforeAll(context: ExtensionContext?) = runTest {
        super.beforeAll(context)
        getService(WebService(Application::issuerModule).run()).main(arrayOf("-l", "trace"))
    }
}