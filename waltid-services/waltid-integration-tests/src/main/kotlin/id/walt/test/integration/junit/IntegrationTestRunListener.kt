package id.walt.test.integration.junit

import id.walt.test.integration.tests.AbstractIntegrationTest
import io.klogging.Klogging
import kotlinx.coroutines.runBlocking
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan

class IntegrationTestRunListener : TestExecutionListener, Klogging {

    override fun testPlanExecutionStarted(testPlan: TestPlan?): Unit = runBlocking {
        logger.info { "Test Execution started" }
        AbstractIntegrationTest.Companion.environment.start()
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan?): Unit = runBlocking {
        logger.info { "Test Execution finished" }
        AbstractIntegrationTest.Companion.environment.shutdown()
    }

}