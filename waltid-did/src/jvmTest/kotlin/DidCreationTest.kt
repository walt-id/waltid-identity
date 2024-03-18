import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import id.walt.did.utils.randomUUID
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.CredentialOffer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    }
}
