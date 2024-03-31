package id.walt.webwallet.usecase.exchange.strategies

import TestUtils
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.PresentationDefinitionFilterParser
import id.walt.webwallet.usecase.exchange.TypeFilter
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals


class FilterPresentationDefinitionMatchStrategyTest {

    private val filterParserMock = mockk<PresentationDefinitionFilterParser>()
    private val sut = FilterPresentationDefinitionMatchStrategy(filterParserMock)
    private val presentationDefinition =
        PresentationDefinition.fromJSON(JsonObject(mapOf("input_descriptors" to emptyArray<String>().toJsonElement())))
    private val filters =
        Json.decodeFromString<List<List<TypeFilter>>>(TestUtils.loadResource("presentation-definition/filters.json"))
    private val credentials = listOf(
        WalletCredential(
            wallet = UUID(),
            id = "array-type",
            document = """
                {
                    "type":
                    [
                        "VerifiableCredential#1"
                    ]
                }
            """.trimIndent(),
            disclosures = null,
            addedOn = Clock.System.now(),
            deletedOn = null,
        ),
        WalletCredential(
            wallet = UUID(),
            id = "primitive-type",
            document = """
                {
                    "type": "VerifiableCredential#1"
                }
            """.trimIndent(),
            disclosures = null,
            addedOn = Clock.System.now(),
            deletedOn = null,
        ),
    )

    @BeforeTest
    fun setup() {
        every { filterParserMock.parse(any()) } returns listOf(filters[0])
    }

    @Test
    fun `match array type`() {
        val result = sut.match(credentials = listOf(credentials[0]), presentationDefinition = presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(
            expected = "VerifiableCredential#1",
            actual = result[0].parsedDocument!!["type"]!!.jsonArray.last().jsonPrimitive.content
        )
    }

    @Test
    fun `match primitive type`() {
        val result = sut.match(credentials = listOf(credentials[1]), presentationDefinition = presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(
            expected = "VerifiableCredential#1", actual = result[0].parsedDocument!!["type"]!!.jsonPrimitive.content
        )
    }
}