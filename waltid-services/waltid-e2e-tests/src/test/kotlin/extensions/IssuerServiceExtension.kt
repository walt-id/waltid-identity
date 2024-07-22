package extensions

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.WebConfig
import id.walt.issuer.main
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

class IssuerServiceExtension : BeforeAllCallback, BeforeTestExecutionCallback {
    override fun beforeAll(context: ExtensionContext?) = runBlocking {
        main(emptyArray())
    }

    override fun beforeTestExecution(context: ExtensionContext?) {
        println(ConfigManager.getConfig<WebConfig>())
    }
}