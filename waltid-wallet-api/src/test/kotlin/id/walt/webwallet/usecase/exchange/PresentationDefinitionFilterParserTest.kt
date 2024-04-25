package id.walt.webwallet.usecase.exchange

import TestUtils
import id.walt.oid4vc.data.dif.PresentationDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationDefinitionFilterParserTest {
    private val sut = PresentationDefinitionFilterParser()
    private val presentationDefinitionSchemaAndConstraints =
        PresentationDefinition.fromJSON(Json.parseToJsonElement(TestUtils.loadResource("presentation-definition/pd-constraints-and-schema.json")).jsonObject)
    private val presentationDefinitionConstraintsNoSchema =
        PresentationDefinition.fromJSON(Json.parseToJsonElement(TestUtils.loadResource("presentation-definition/pd-constraints-no-schema.json")).jsonObject)
    private val presentationDefinitionSchemaNoConstraints =
        PresentationDefinition.fromJSON(Json.parseToJsonElement(TestUtils.loadResource("presentation-definition/pd-schema-no-constraints.json")).jsonObject)
    private val presentationDefinitionVcPathConstraint =
        PresentationDefinition.fromJSON(Json.parseToJsonElement(TestUtils.loadResource("presentation-definition/pd-constraints-vc-path.json")).jsonObject)
    private val filters = listOf(
        TypeFilter(
            path = listOf("credentialSubject.property"),
            type = "string",
            pattern = "value"
        ),
        TypeFilter(
            path = listOf("type"),
            type = "string",
            pattern = "VerifiableCredential#1"
        )
    )

    @Test
    fun `given presentation definition containing an input descriptor with one constraint and schema, when parsing, then the result contains filters for both`() {
        val result = sut.parse(presentationDefinitionSchemaAndConstraints)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "VerifiableCredential#1", actual = result[0].credential)
        assertEquals(expected = listOf(filters[0], filters[1]), actual = result[0].filters)
    }

    @Test
    fun `given presentation definition containing an input descriptor with one constraint and no schema, when parsing, then the result contains filter only for constraint`() {
        val result = sut.parse(presentationDefinitionConstraintsNoSchema)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "VerifiableCredential#1", actual = result[0].credential)
        assertEquals(expected = listOf(filters[0]), actual = result[0].filters)
    }

    @Test
    fun `given presentation definition containing an input descriptor with schema and no constraints, when parsing, then the result contains filter only for schema`() {
        val result = sut.parse(presentationDefinitionSchemaNoConstraints)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "VerifiableCredential#1", actual = result[0].credential)
        assertEquals(expected = listOf(filters[1]), actual = result[0].filters)
    }

    @Test
    fun `given presentation definition containing an input descriptor with a constraint having a vc path, when parsing, then the result contains filter with correct path`() {
        val result = sut.parse(presentationDefinitionVcPathConstraint)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = "VerifiableCredential#1", actual = result[0].credential)
        assertEquals(expected = listOf(filters[0]), actual = result[0].filters)
    }
}