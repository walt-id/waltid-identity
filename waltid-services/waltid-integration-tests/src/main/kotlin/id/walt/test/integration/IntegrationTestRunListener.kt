package id.walt.test.integration

import id.walt.test.integration.tests.AbstractIntegrationTest.Companion.environment
import io.klogging.Klogging
import kotlinx.coroutines.runBlocking
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan


class IntegrationTestRunListener : TestExecutionListener, Klogging {

    override fun testPlanExecutionStarted(testPlan: TestPlan?): Unit = runBlocking {
        logger.info { "Test Execution started" }
        environment.start()
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan?): Unit = runBlocking {
        logger.info { "Test Execution finished" }
        environment.shutdown()
    }

}