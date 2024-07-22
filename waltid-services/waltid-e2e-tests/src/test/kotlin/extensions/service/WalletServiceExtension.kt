package extensions.service

import id.walt.commons.web.WebService
import id.walt.webwallet.getService
import id.walt.webwallet.webWalletModule

import io.ktor.server.application.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtensionContext

class WalletServiceExtension : BaseServiceExtension() {
    override fun beforeAll(context: ExtensionContext?) = runTest {
        super.beforeAll(context)
        getService(WebService(Application::webWalletModule).run()).main(arrayOf("-l", "trace"))
    }
}