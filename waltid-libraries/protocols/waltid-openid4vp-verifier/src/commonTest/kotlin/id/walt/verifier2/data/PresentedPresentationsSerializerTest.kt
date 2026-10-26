package id.walt.verifier2.data

import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.credentials.presentations.formats.VerifiablePresentation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PresentedPresentationsSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun presentation(jwt: String) = JwtVcJsonPresentation(
        jwt = jwt,
        payload = buildJsonObject { put("nonce", "n") },
        issuer = "did:example:holder",
        audience = "redirect_uri:https://verifier.example/response",
        nonce = "n",
        vp = buildJsonObject { },
        credentials = null,
    )

    @Test
    fun newSessionsStoreAListPerQueryId() {
        val encoded = json.encodeToString(
            PresentedPresentationsSerializer,
            mapOf("ids" to listOf(presentation("first"), presentation("second"))),
        )
        val stored = json.parseToJsonElement(encoded).jsonObject.getValue("ids")

        assertEquals(2, assertIs<JsonArray>(stored).size)
        val decoded = json.decodeFromString(PresentedPresentationsSerializer, encoded)
        assertEquals(listOf("first", "second"), decoded.getValue("ids").map { (it as JwtVcJsonPresentation).jwt })
    }

    @Test
    fun aPreviouslyStoredSinglePresentationStillLoads() {
        val single = json.encodeToString(VerifiablePresentation.serializer(), presentation("only"))
        val legacy = buildJsonObject { put("ids", json.parseToJsonElement(single)) }

        val decoded = json.decodeFromString(PresentedPresentationsSerializer, legacy.toString())

        assertEquals(listOf("only"), decoded.getValue("ids").map { (it as JwtVcJsonPresentation).jwt })
    }
}
