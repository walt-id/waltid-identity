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
import id.walt.webwallet.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

class FilterPresentationDefinitionMatchStrategyTest {

    private val filterParserMock = mockk<PresentationDefinitionFilterParser>()
    private val sut = FilterPresentationDefinitionMatchStrategy(filterParserMock)
    private val presentationDefinition =
        PresentationDefinition.fromJSON(JsonObject(mapOf("input_descriptors" to emptyArray<String>().toJsonElement())))
    private val vc1Filter =
        Json.decodeFromString<List<FilterData>>(TestUtils.loadResource("presentation-definition/filters/filter-vc1.json"))
    private val patternFilter =
        Json.decodeFromString<List<FilterData>>(TestUtils.loadResource("presentation-definition/filters/filter-pattern.json"))
    private val compositeFilter =
        Json.decodeFromString<List<FilterData>>(TestUtils.loadResource("presentation-definition/filters/filter-composite.json"))
    private val credentials = listOf(
        WalletCredential(
            wallet = randomUUID(),
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
            format = CredentialFormat.ldp_vc
        ),
        WalletCredential(
            wallet = randomUUID(),
            id = "primitive-type",
            document = """
                {
                    "type": "VerifiableCredential#1",
                    "credentialSubject": {
                        "firstName": "name"
                    }
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

    @Test
    fun `match array type`() {
        every { filterParserMock.parse(any()) } returns vc1Filter
        val result = sut.match(credentials = listOf(credentials[0]), presentationDefinition = presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(
            expected = "VerifiableCredential#1",
            actual = JsonUtils.tryGetData(result[0].parsedDocument!!, "type")!!.jsonArray.last().jsonPrimitive.content
        )
    }

    @Test
    fun `match primitive type`() {
        every { filterParserMock.parse(any()) } returns vc1Filter
        val result = sut.match(credentials = listOf(credentials[1]), presentationDefinition = presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(
            expected = "VerifiableCredential#1",
            actual = JsonUtils.tryGetData(result[0].parsedDocument!!, "type")!!.jsonPrimitive.content
        )
    }

    @Test
    fun `match constraint primitive type`() {
        every { filterParserMock.parse(any()) } returns patternFilter
        val result = sut.match(credentials = listOf(credentials[1]), presentationDefinition = presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(
            expected = "VerifiableCredential#1",
            actual = JsonUtils.tryGetData(result[0].parsedDocument!!, "type")!!.jsonPrimitive.content
        )
        assertEquals(
            expected = "name",
            actual = JsonUtils.tryGetData(
                result[0].parsedDocument!!,
                "credentialSubject.firstName"
            )!!.jsonPrimitive.content
        )
    }

    @Test
    fun `given presentation-definition with multiple input-descriptors, when matching credentials, then all matching credentials are returned`() {
        every { filterParserMock.parse(any()) } returns compositeFilter
        val result = sut.match(
            credentials = listOf(credentials[1], credentials[2]),
            presentationDefinition = presentationDefinition
        )
        assertEquals(expected = 2, actual = result.size)
        assertEquals(
            expected = "VerifiableCredential#1",
            actual = JsonUtils.tryGetData(result[0].parsedDocument!!, "type")!!.jsonPrimitive.content
        )
        assertEquals(
            expected = "name",
            actual = JsonUtils.tryGetData(
                result[0].parsedDocument!!,
                "credentialSubject.firstName"
            )!!.jsonPrimitive.content
        )
        assertEquals(
            expected = "VerifiableCredential#2",
            actual = JsonUtils.tryGetData(result[1].parsedDocument!!, "type")!!.jsonPrimitive.content
        )
    }
}
