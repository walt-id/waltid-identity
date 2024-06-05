import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

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
}
