package id.walt.webwallet.usecase.exchange

import TestUtils
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.test.Test
import kotlin.test.assertEquals


class FilterPresentationDefinitionMatchStrategyTest {

    private val sut = FilterPresentationDefinitionMatchStrategy()
    private val presentationDefinition = TestUtils.loadResource("presentation-definition.json")
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

    @Test
    fun `match array type`() {
        val pd = PresentationDefinition.fromJSON(Json.decodeFromString(presentationDefinition))
        val result = sut.match(listOf(credentials[0]), pd)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(
            expected = "VerifiableCredential#1",
            actual = result[0].parsedDocument!!["type"]!!.jsonArray.last().jsonPrimitive.content
        )
    }

    @Test
    fun `match primitive type`() {
        val pd = PresentationDefinition.fromJSON(Json.decodeFromString(presentationDefinition))
        val result = sut.match(listOf(credentials[1]), pd)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(
            expected = "VerifiableCredential#1", actual = result[0].parsedDocument!!["type"]!!.jsonPrimitive.content
        )
    }
}