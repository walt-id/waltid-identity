package extensions.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

abstract class BaseServiceExtension : BeforeAllCallback, BeforeTestExecutionCallback {
    protected val logger = KotlinLogging.logger {}
    override fun beforeAll(context: ExtensionContext?) = runTest {
        logger.debug { "${this.javaClass.name}: before all" }
    }

    override fun beforeTestExecution(context: ExtensionContext?) {
        logger.debug { "${this.javaClass.name}: before test execution" }
    }
}