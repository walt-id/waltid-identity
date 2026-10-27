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

    private fun Map<String, List<VerifiablePresentation>>.jwts(queryId: String) =
        getValue(queryId).map { (it as JwtVcJsonPresentation).jwt }

    @Test
    fun newSessionsStoreAListPerQueryId() {
        val encoded = json.encodeToString(
            PresentedPresentationsSerializer,
            mapOf("ids" to listOf(presentation("first"), presentation("second"))),
        )
        val stored = json.parseToJsonElement(encoded).jsonObject.getValue("ids")

        assertEquals(2, assertIs<JsonArray>(stored).size)
        val decoded = json.decodeFromString(PresentedPresentationsSerializer, encoded)
        assertEquals(listOf("first", "second"), decoded.jwts("ids"))
    }

    @Test
    fun aPreviouslyStoredSinglePresentationStillLoads() {
        val single = json.encodeToString(VerifiablePresentation.serializer(), presentation("only"))
        val legacy = buildJsonObject { put("ids", json.parseToJsonElement(single)) }

        val decoded = json.decodeFromString(PresentedPresentationsSerializer, legacy.toString())

        assertEquals(listOf("only"), decoded.jwts("ids"))
    }

    @Test
    fun aPreviouslyStoredSinglePresentationStillLoadsFromAJsonTree() {
        val single = json.encodeToJsonElement(VerifiablePresentation.serializer(), presentation("only"))
        val legacy = buildJsonObject { put("ids", single) }

        val decoded = json.decodeFromJsonElement(PresentedPresentationsSerializer, legacy)

        assertEquals(listOf("only"), decoded.jwts("ids"))
    }

    @Test
    fun aSessionMixingBothShapesLoads() {
        val single = json.encodeToJsonElement(VerifiablePresentation.serializer(), presentation("legacy"))
        val list = json.encodeToJsonElement(
            PresentationsOfQuerySerializer,
            listOf(presentation("first"), presentation("second")),
        )
        val stored = buildJsonObject { put("old", single); put("new", list) }

        val decoded = json.decodeFromJsonElement(PresentedPresentationsSerializer, stored)

        assertEquals(listOf("legacy"), decoded.jwts("old"))
        assertEquals(listOf("first", "second"), decoded.jwts("new"))
    }
}
