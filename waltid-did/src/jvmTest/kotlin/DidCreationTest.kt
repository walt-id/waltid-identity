import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import id.walt.did.utils.randomUUID
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.util.http
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.test.*

class DidCreationTest {

    private val registrar = DidWebRegistrar()

    @Test
    fun checkDidDocumentIsValidJson() = runTest {
        val result = registrar.register(DidWebCreateOptions("localhost", "/xyz/abc"))

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

    @Test
    fun ebsiDidOnboarding() = runTest {
        val mainKey = LocalKey.generate(KeyType.secp256k1)
        val vcSignKey = LocalKey.generate(KeyType.secp256r1)
        val didMSI = UUID.randomUUID().let { ByteBuffer.allocate(17)
            .put(0x01)
            .putLong(1, it.mostSignificantBits)
            .putLong(9, it.leastSignificantBits).array()
        }.let { MultiBaseUtils.encodeMultiBase58Btc(it) }
        assertEquals('z', didMSI.first())
        val did = "did:ebsi:$didMSI"
        val taoIssuer = "https://api-conformance.ebsi.eu/conformance/v3/issuer-mock"
        val issuerMetadata = OpenID4VCI.resolveCIProviderMetadata(taoIssuer)
        assertEquals(taoIssuer, issuerMetadata.credentialIssuer)
        val authMetadata = OpenID4VCI.resolveAuthProviderMetadata(issuerMetadata.authorizationServer!!)
        assertEquals(issuerMetadata.authorizationServer, authMetadata.issuer)

        val clientId = "https://test.walt.id"
        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code), clientId,
            scope = setOf("openid"),
            redirectUri = "https://test.walt.id",
            authorizationDetails = listOf(
                AuthorizationDetails.fromLegacyCredentialParameters(
                    CredentialFormat.jwt_vc, issuerMetadata.credentialsSupported!!.first { it.types?.contains("VerifiableAuthorisationToOnboard") == true }.types!!                )
            ),
            clientMetadata = OpenIDClientMetadata(customParameters = mapOf(
                "jwks_uri" to JsonPrimitive("https://my-issuer.eu/suffix/xyz/jwks"),
                "authorization_endpoint" to JsonPrimitive("openid:")
            ))
        )

        val signedRequestObject = mainKey.signJws(
            authReq.toRequestObjectPayload(clientId, authMetadata.issuer!!).toString().toByteArray(),
            mapOf("kid" to mainKey.getKeyId())
        )

        val httpResp = http.get(authMetadata.authorizationEndpoint!!) {
            url { parameters.appendAll(parametersOf(authReq.toHttpParametersWithRequestObject(signedRequestObject)))
            println(buildString())
            }
        }
        println(httpResp.bodyAsText())
    }
}
