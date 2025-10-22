package id.walt.test.integration.junit

import id.walt.test.integration.tests.AbstractIntegrationTest
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

class E2eTestReportExtension : AfterTestExecutionCallback {

    override fun afterTestExecution(context: ExtensionContext?) {
        val testName = "${context?.testClass?.orElse(null)?.name}::${context?.displayName}"
        AbstractIntegrationTest.environment.e2e.addTestResult(
            testName,
            when (context?.executionException?.isPresent) {
                false -> Result.success(Unit)
                else -> Result.failure(context!!.executionException.get())
            }
        )
    }
}