import id.walt.w3c.PresentationBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationBuilderTest {

    @Test
    fun testPresentationBuilder() {
        val vp = PresentationBuilder().apply {
            did = "did:key:123456789"    // sets sub, iss, vp.holder

            // nbf, iat, jti set automatically to sane default values

            nonce = "ABC123DEF456GHI789JKL"

            // vp.context, vp.type, vp.id set automatically to sane default values

            addCredential()
        }.buildPresentationJson()

        val claims = vp.jsonObject
        val presentation = claims.getValue("vp").jsonObject
        assertEquals(JsonPrimitive("did:key:123456789"), claims["sub"])
        assertEquals(JsonPrimitive("did:key:123456789"), claims["iss"])
        assertEquals(JsonPrimitive("did:key:123456789"), presentation["holder"])
        assertEquals(JsonPrimitive("ABC123DEF456GHI789JKL"), claims["nonce"])
        assertEquals(Json.parseToJsonElement("""["https://www.w3.org/2018/credentials/v1"]"""), presentation["@context"])
        assertEquals(Json.parseToJsonElement("""["VerifiablePresentation"]"""), presentation["type"])
        assertEquals(JsonArray(emptyList()), presentation["verifiableCredential"])
        val id = claims.getValue("jti").jsonPrimitive.content
        assertTrue(id.startsWith("urn:uuid:") && id.length > "urn:uuid:".length)
        assertEquals(JsonPrimitive(id), presentation["id"])
    }

}
