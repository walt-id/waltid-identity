import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.did.dids.document.DidEbsiBaseDocument
import id.walt.did.dids.document.DidEbsiDocument
import id.walt.did.dids.registrar.dids.DidEbsiCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.ebsi.DidEbsiRegistrar
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import id.walt.did.dids.resolver.DidResolver
import id.walt.did.dids.resolver.local.DidEbsiResolver
import id.walt.did.utils.randomUUID
import id.walt.ebsi.accreditation.AccreditationClient
import id.walt.ebsi.did.DidEbsiService
import id.walt.ebsi.eth.TransactionService
import id.walt.ebsi.rpc.EbsiRpcRequests
import id.walt.ebsi.rpc.SignedTransactionResponse
import id.walt.ebsi.rpc.UnsignedTransactionResponse
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.PresentationBuilder
import id.walt.oid4vc.util.http
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DidCreationTest {

    private val registrar = DidWebRegistrar()

    @Test
    fun checkDidDocumentIsValidJson() = runTest {
        val result = registrar.register(DidWebCreateOptions("localhost", "/xyz/abc").also { println(it.config) })

        val didDoc1 = result.didDocument.toJsonObject()
        val didDoc2 = Json.parseToJsonElement(
            result.didDocument.toString()
        ).jsonObject

        assertEquals(didDoc1, didDoc2)
    }

    @Test
    fun checkDidDocumentNotWrappedInContent() = runTest {
        val result = registrar.register(DidWebCreateOptions("localhost", "/xyz/abc"))

        val didDoc1 = result.didDocument.toJsonObject()
        assertNull(didDoc1["content"])

        val didDoc2 = Json.parseToJsonElement(
            result.didDocument.toString()
        ).jsonObject
        assertNull(didDoc2["content"])
    }

    @Test
    fun checkContextExistsInDid() = runTest {
        val result = registrar
            .register(DidWebCreateOptions("localhost", "/abc/xyz"))

        val didDoc1 = result.didDocument.toString()
        println("DID doc: $didDoc1")
        assertContains(didDoc1, "\"@context\":")

        val didDoc2 = result.didDocument.toJsonObject()
        assertNotNull(didDoc2["@context"])
        assertNotNull(didDoc2["@context"]?.jsonArray)
        assertTrue { didDoc2["@context"]!!.jsonArray.isNotEmpty() }
    }

    @Test
    fun checkReferencedDidMethods() = runTest {
        val resultWeb = registrar
            .register(DidWebCreateOptions("localhost", "/abc/xyz"))
        val resultKey = DidKeyRegistrar().register(DidKeyCreateOptions())

        arrayOf(resultKey, resultWeb).forEach { result ->
            val didDoc1 = result.didDocument.toString()
            println("DID doc: $didDoc1")
            val id =
                result.didDocument["verificationMethod"]?.jsonArray?.get(0)?.jsonObject?.get("id")?.jsonPrimitive?.content
            val refId = result.didDocument["assertionMethod"]?.jsonArray?.get(0)?.jsonPrimitive?.content
            assertEquals(id, refId)
        }
    }

    val CLIENT_MOCK_PORT = 5000
    val CLIENT_MOCK_URL = "https://21b4-62-178-27-231.ngrok-free.app/client-mock"//"http://192.168.0.122:5000/client-mock"
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
    fun ebsiDidOnboarding() = runTest {
        startClientMockServer()
        val did = DidEbsiService.generateRandomDid()
        val taoIssuer = "https://api-conformance.ebsi.eu/conformance/v3/issuer-mock"

        // TODO: reorganize this code into DidEbsiRegistrar, accreditation client parameters needed in did registration options
        val accreditationClient = AccreditationClient(CLIENT_MOCK_URL, did, CLIENT_VCSIGN_KEY, taoIssuer)
        val authToOnboardRes = accreditationClient.getAuthorisationToOnboard()

        println(authToOnboardRes.credential)

        val presDefResp = http.get("https://api-conformance.ebsi.eu/authorisation/v4/presentation-definitions") {
            parameter("scope", "openid didr_invite")
        }
        println(presDefResp.bodyAsText())
        val presDef = PresentationDefinition.fromJSONString(presDefResp.bodyAsText())

        //OpenID4VP.createPresentationRequest(PresentationDefinitionParameter.fromPresentationDefinitionScope("openid didr_invite"),
        //    clientId = CLIENT_MOCK_URL, clientIdScheme = ClientIdScheme.RedirectUri, )

        val didResult = DidEbsiRegistrar().registerByKey(CLIENT_MAIN_KEY, DidEbsiCreateOptions(
            5, authToOnboardRes.credential!!, authToOnboardRes.cNonce
        ))
        val didEbsiDoc = didResult.didDocument.let { Json.decodeFromJsonElement<DidEbsiDocument>(it.toJsonObject()) }
        assertEquals("$did#${CLIENT_MAIN_KEY.getKeyId()}", didEbsiDoc.verificationMethod!!.first().id)
        Thread.sleep(5000)
        val resolveDidHttpResponse = http.get("https://api-conformance.ebsi.eu/did-registry/v5/identifiers/${URLEncoder.encode(did)}")
        assertEquals(HttpStatusCode.OK, resolveDidHttpResponse.status)
        println(resolveDidHttpResponse.bodyAsText())
        val resolverResult = DidEbsiResolver().resolve(did)
        assertEquals(true, resolverResult.isSuccess)
        println(resolverResult.getOrNull()!!.toString())
        // TODO:
        //      register other key types for did?
        //      VerifiableAccreditationToAttest
        //      more steps from https://hub.ebsi.eu/conformance/build-solutions/accredit-and-authorise-functional-flows
        //      Reorganize test code into production code!!

    }
}
