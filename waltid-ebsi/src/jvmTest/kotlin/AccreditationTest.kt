import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.ebsi.Delay
import id.walt.ebsi.EbsiEnvironment
import id.walt.ebsi.did.DidEbsiService
import id.walt.ebsi.did.DidRegistrationOptions
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AccreditationTest {
  val CLIENT_MOCK_PORT = 5000
  val CLIENT_MOCK_URL = "https://bb3c-62-178-27-231.ngrok-free.app/client-mock"//"http://192.168.0.122:5000/client-mock"
  val CLIENT_MAIN_KEY = runBlocking { JWKKey.generate(KeyType.secp256k1) }
  val CLIENT_VCSIGN_KEY = runBlocking { JWKKey.generate(KeyType.secp256r1) }
  fun startClientMockServer() {
    embeddedServer(Netty, port = CLIENT_MOCK_PORT) {
      install(ContentNegotiation) {
        json()
      }
      routing {
        get("/client-mock") {
          println("CLIENT MOCK called")
          println(call.parameters.toString())
        }
        get("/client-mock/jwks") {
          call.respond(buildJsonObject {
            put("keys", buildJsonArray {
              add(CLIENT_MAIN_KEY.exportJWKObject())
              add(CLIENT_VCSIGN_KEY.exportJWKObject().also { println(it.toString()) })
            })
          })
        }
      }
    }.start()
  }

  @OptIn(ExperimentalStdlibApi::class)
  @Test
  fun trustedIssuerAccreditationTest() = runTest {

    // #### DID Onboarding ####
    val did = ebsiDidOnboarding()

    // #### Get accredited as a Trusted Issuer ####
    // TODO: get accredited


    // #### Issue & revoke ####
    // TODO: test issuing and revoking

    // Notes:
    //      VerifiableAccreditationToAttest
    //      more steps from https://hub.ebsi.eu/conformance/build-solutions/accredit-and-authorise-functional-flows
    //      Reorganize test code into production code!!
  }

  suspend fun ebsiDidOnboarding(): String {
    startClientMockServer()
    val taoIssuer = "https://api-conformance.ebsi.eu/conformance/v3/issuer-mock"

    val didResult = assertDoesNotThrow {
      DidEbsiService.generateAndRegisterDid(CLIENT_MAIN_KEY, CLIENT_VCSIGN_KEY,
      DidRegistrationOptions(CLIENT_MOCK_URL, taoIssuer, ebsiEnvironment = EbsiEnvironment.conformance, didRegistryVersion = 5))
    }

    var resolverResult: Result<JsonObject>? = null
    for (i in 1..10) {
      resolverResult = kotlin.runCatching { DidEbsiService.resolve(didResult, EbsiEnvironment.conformance, 5) }
      if(!resolverResult.isSuccess || resolverResult.getOrNull()?.get("assertionMethod") == null)
      {
        println("Waiting for assertionMethod to become visible in DID document...")
        Delay.delay(2000)
      } else break
    }
    assertNotNull(resolverResult)
    assertEquals(true, resolverResult.isSuccess)
    assertEquals("${didResult}#${CLIENT_MAIN_KEY.getKeyId()}", resolverResult.getOrNull()?.get("verificationMethod")?.jsonArray?.first()?.jsonObject?.get("id")?.jsonPrimitive?.content)
    println(resolverResult.getOrNull()!!.toString())
    val didEbsiDocument = resolverResult.getOrNull()!!
    assertNotNull(didEbsiDocument.get("capabilityInvocation"))
    assertNotNull(didEbsiDocument.get("authentication"))
    assertNotNull(didEbsiDocument.get("assertionMethod"))
    assertContains(didEbsiDocument.get("capabilityInvocation")!!.jsonArray.map { it.jsonPrimitive.content }, "${didResult}#${CLIENT_MAIN_KEY.getKeyId()}")
    assertContains(didEbsiDocument.get("authentication")!!.jsonArray.map { it.jsonPrimitive.content }, "${didResult}#${CLIENT_VCSIGN_KEY.getKeyId()}")
    assertContains(didEbsiDocument.get("assertionMethod")!!.jsonArray.map { it.jsonPrimitive.content }, "${didResult}#${CLIENT_VCSIGN_KEY.getKeyId()}")

    return didResult
  }
}
