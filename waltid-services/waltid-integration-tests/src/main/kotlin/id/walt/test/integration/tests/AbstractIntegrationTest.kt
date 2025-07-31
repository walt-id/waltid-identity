package id.walt.test.integration.tests

import id.walt.test.integration.environment.InMemoryCommunityStackEnvironment
import id.walt.test.integration.junit.LogTestStartExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(LogTestStartExtension::class)
abstract class AbstractIntegrationTest {

    companion object {
        val environment = InMemoryCommunityStackEnvironment()

        // start of environment is done by the
        // id.walt.test.integration.junit.IntegrationTestRunListener

        // shutdown is done by the
        // id.walt.test.integration.junit.IntegrationTestRunListener
        // only the listener does know, when the last test is executed

        suspend fun getDefaultAccountWalletApi() = environment.getDefaultAccountWalletApi()

    }

}