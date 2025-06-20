@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.exchange.strategies

import TestUtils
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.UuidUtils.randomUUID
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.usecase.exchange.PresentationDefinitionFilterParser
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FilterNoMatchPresentationDefinitionMatchStrategyTest {

    private val filterParserMock = mockk<PresentationDefinitionFilterParser>()
    private val sut = FilterNoMatchPresentationDefinitionMatchStrategy(filterParserMock)

    private val presentationDefinition =
        PresentationDefinition.fromJSON(JsonObject(mapOf("input_descriptors" to emptyArray<String>().toJsonElement())))
    private val vc1Filter =
        Json.decodeFromString<List<FilterData>>(TestUtils.loadResource("presentation-definition/filters/filter-vc1.json"))
    private val credentials = listOf(
        WalletCredential(
            wallet = randomUUID(),
            id = "array-type",
            document = """
                {
                    "type":
                    [
                        "VerifiableCredential#2"
                    ]
                }
            """.trimIndent(),
            disclosures = null,
            addedOn = Clock.System.now(),
            deletedOn = null,
            format = CredentialFormat.ldp_vc
        ),
        WalletCredential(
            wallet = randomUUID(),
            id = "primitive-type",
            document = """
                {
                    "type": "VerifiableCredential#2"
                }
            """.trimIndent(),
            disclosures = null,
            addedOn = Clock.System.now(),
            deletedOn = null,
            format = CredentialFormat.ldp_vc
        ),
    )

    @BeforeTest
    fun setup() {
        every { filterParserMock.parse(any()) } returns listOf(vc1Filter[0])
    }

    @Test
    fun `no match array type`() {
        val result = sut.match(credentials = listOf(credentials[0]), presentationDefinition = presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = vc1Filter, result)
    }

    @Test
    fun `no match primitive type`() {
        val result = sut.match(credentials = listOf(credentials[1]), presentationDefinition = presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = vc1Filter, result)
    }
}
