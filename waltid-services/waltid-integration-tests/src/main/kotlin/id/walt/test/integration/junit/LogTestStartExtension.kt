package id.walt.test.integration.junit

import io.klogging.Klogging
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

class LogTestStartExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback, Klogging {
    override fun beforeTestExecution(context: ExtensionContext?) = runBlocking {
        logger.error { "===============================================================" }
        logger.error { "${context?.testClass?.orElse(null)?.name}::${context?.displayName}" }
        logger.error { "===============================================================" }
    }

    override fun afterTestExecution(context: ExtensionContext?) = runBlocking {
        logger.error { "***************************************************************" }
    }
}