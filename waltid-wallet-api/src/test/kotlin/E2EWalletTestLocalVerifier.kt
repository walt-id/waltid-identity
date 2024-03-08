//import PresentationDefinition.Companion.minimalPresentationDefinition
//import PresentationDefinition.Companion.vpPoliciesDefinition
//import com.zaxxer.hikari.HikariConfig
//import com.zaxxer.hikari.HikariDataSource
//import id.walt.issuer.issuerModule
//import id.walt.verifier.base.config.OIDCVerifierServiceConfig
//import id.walt.verifier.verifierModule
//import id.walt.webwallet.config.ConfigManager
//import id.walt.webwallet.config.DatasourceConfiguration
//import id.walt.webwallet.config.WebConfig
//import id.walt.webwallet.utils.WalletHttpClients
//import id.walt.webwallet.webWalletModule
//import id.walt.webwallet.webWalletSetup
//import io.ktor.server.testing.*
//import kotlinx.serialization.json.JsonObject
//import kotlinx.serialization.json.jsonPrimitive
//import java.io.File
//import java.nio.file.Files
//import java.nio.file.Paths
//import kotlin.io.path.Path
//import kotlin.io.path.absolutePathString
//import kotlin.test.Test
//import kotlin.test.assertNotEquals
//import kotlin.test.assertTrue
//import id.walt.verifier.base.config.ConfigManager as VerifierConfigManager
//
//
//class E2EWalletTestLocalVerifier : E2EWalletTestLocal() {
//
//    override fun setupTestVerifier() {
//        VerifierConfigManager.preloadConfig("verifier-service", OIDCVerifierServiceConfig("http://localhost"))
//
//        VerifierConfigManager.loadConfigs(emptyArray())
//    }
//    @Test
//    fun e2eTestPolicyList() = testApplication {
//        runApplication()
//        val list: JsonObject = testPolicyList()
//
//        assertNotEquals(0, list.size)
//        list.keys.forEach {
//            println("$it -> ${list[it]?.jsonPrimitive?.content}")
//        }
//    }
//
//    @Test
//    fun e2eTestVerify() = testApplication {
//        runApplication()
//        var url = testVerifyCredential(minimalPresentationDefinition)
//        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
//        println("verify Url = $url")
//
//        url = testVerifyCredential(vpPoliciesDefinition)
//        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
//        println("verify Url = $url")
//    }
//
//    @Test
//    fun e2eTestPresentationRequest() = testApplication {
//        runApplication()
//        login()
//        getUserToken()
//        localWalletClient = newClient(token)
//
//        listAllWallets() // sets the walletId
//        val url = testVerifyCredential(minimalPresentationDefinition)
//        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
//        println("verify Url = $url")
//
//        val parsedRequest = testResolvePresentationRequest(url)
//        println("Parsed Request = $parsedRequest")
//    }
//
//}
