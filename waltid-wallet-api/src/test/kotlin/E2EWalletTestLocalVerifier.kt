import PresentationDefinition.Companion.minimalPresentationDefinition
import PresentationDefinition.Companion.vpPoliciesDefinition
import io.ktor.server.testing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


class E2EWalletTestLocalVerifier : E2EWalletTestLocal() {
    
    @Test
    fun e2eTestPolicyList() = testApplication {
        runApplication()
        val list: JsonObject = testPolicyList()
        
        assertNotEquals(0, list.size)
        list.keys.forEach {
            println("$it -> ${list[it]?.jsonPrimitive?.content}")
        }
    }
    
    @Test
    fun e2eTestVerify() = testApplication {
        runApplication()
        var url = testVerifyCredential(minimalPresentationDefinition)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("verify Url = $url")
        
        url = testVerifyCredential(vpPoliciesDefinition)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("verify Url = $url")
    }
    
    @Test
    fun e2eTestPresentationRequest() = testApplication {
        runApplication()
        login()
        getUserToken()
        localWalletClient = newClient(token)
        
        listAllWallets() // sets the walletId
        val url = testVerifyCredential(minimalPresentationDefinition)
        assertTrue(url.startsWith("openid4vp://authorize?response_type=vp_token"))
        println("verify Url = $url")
        
        val parsedRequest = testResolvePresentationRequest(url)
        println("Parsed Request = $parsedRequest")
    }
    
}
