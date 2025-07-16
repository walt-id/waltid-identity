@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.exchange.strategies

import TestUtils
import id.walt.crypto.utils.UuidUtils.randomUUID
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.usecase.exchange.TypeFilter
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DescriptorNoMatchPresentationDefinitionMatchStrategyTest {

    private val sut = DescriptorNoMatchPresentationDefinitionMatchStrategy()
    private val presentationDefinition =
        PresentationDefinition.fromJSON(Json.decodeFromString(TestUtils.loadResource("presentation-definition/definition.json")))
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
    )
    private val expectedFilterData =
        listOf(
            element = FilterData(
                credential = "VerifiableCredential#1",
                filters = listOf(TypeFilter(path = emptyList(), type = null, pattern = "VerifiableCredential#1"))
            )
        )

    @Test
    fun match() {
        val result = sut.match(credentials, presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = expectedFilterData, actual = result)
    }
}
