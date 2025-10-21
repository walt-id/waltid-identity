package id.walt.test.integration.tests

import id.walt.test.integration.environment.InMemoryCommunityStackEnvironment
import id.walt.test.integration.environment.api.issuer.IssuerApi
import id.walt.test.integration.environment.api.verifier.VerifierApi
import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.test.integration.environment.api.wallet.WalletContainerApi
import id.walt.test.integration.junit.E2eTestReportExtension
import id.walt.test.integration.junit.LogTestStartExtension
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(LogTestStartExtension::class, E2eTestReportExtension::class)
abstract class AbstractIntegrationTest {

    companion object {
        val environment = InMemoryCommunityStackEnvironment()

        // start of environment is done by the
        // id.walt.test.integration.junit.IntegrationTestRunListener

        // shutdown is done by the
        // id.walt.test.integration.junit.IntegrationTestRunListener
        // only the listener does know, when the last test is executed

        lateinit var issuerApi: IssuerApi
        lateinit var verifierApi: VerifierApi
        lateinit var walletContainerApi: WalletContainerApi
        lateinit var defaultWalletApi: WalletApi

        @JvmStatic
        @BeforeAll
        fun loadWalletAndDefaultDid(): Unit = runBlocking {
            walletContainerApi = environment.getDefaultAccountWalletContainerApi()
            issuerApi = environment.getIssuerApi()
            verifierApi = environment.getVerifierApi()
            defaultWalletApi = walletContainerApi.selectDefaultWallet()
            deleteAllCategoriesAndCredentialOfDefaultWallet()
        }

        @JvmStatic
        @AfterAll
        fun deleteAllCategoriesAndCredentialOfDefaultWallet(): Unit = runBlocking {
            defaultWalletApi.listCategories().map {
                it["name"]?.jsonPrimitive?.content
            }.forEach {
                defaultWalletApi.deleteCategory(it!!)
            }
            // The test expects an empty wallet in the beginning, so delete all
            // credentials first, before test start
            defaultWalletApi.listCredentials().forEach {
                defaultWalletApi.deleteCredential(it.id)
            }
        }
    }
}
