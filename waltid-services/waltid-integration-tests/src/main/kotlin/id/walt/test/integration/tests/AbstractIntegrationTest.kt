package id.walt.test.integration.tests

import id.walt.test.integration.environment.InMemoryCommunityStackEnvironment

abstract class AbstractIntegrationTest {

    companion object {
        val environment = InMemoryCommunityStackEnvironment()

        // start of environment is done by the
        // id.walt.test.integration.IntegrationTestRunListener

        // shutdown is done by the
        // id.walt.test.integration.IntegrationTestRunListener
        // only the listener does know, when the last test is executed
    }
}