package id.walt.test.integration.tests

import id.walt.test.integration.environment.InMemoryCommunityStackEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

abstract class AbstractIntegrationTest {

    companion object {
        lateinit var environment: InMemoryCommunityStackEnvironment

        @JvmStatic
        @BeforeAll
        fun initEnvironment() = runBlocking {
            environment = InMemoryCommunityStackEnvironment()
            environment.start()
        }

        @JvmStatic
        @AfterAll
        fun shutdownEnvironment() = runBlocking {
            environment.shutdown()
        }

    }
}