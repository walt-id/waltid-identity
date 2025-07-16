@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.exchange

import TestUtils
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.UuidUtils.randomUUID
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.usecase.exchange.strategies.PresentationDefinitionMatchStrategy
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

class NoMatchPresentationDefinitionCredentialsUseCaseTest {

    private val credentialService = mockk<CredentialsService>()
    private val matchStrategy = mockk<PresentationDefinitionMatchStrategy<List<FilterData>>>()
    private val vc1Filter =
        Json.decodeFromString<List<FilterData>>(TestUtils.loadResource("presentation-definition/filters/filter-vc1.json"))
    private val vc2Filter =
        Json.decodeFromString<List<FilterData>>(TestUtils.loadResource("presentation-definition/filters/filter-vc2.json"))
    private val patternFilter =
        Json.decodeFromString<List<FilterData>>(TestUtils.loadResource("presentation-definition/filters/filter-pattern.json"))
    private val presentationDefinition =
        PresentationDefinition.fromJSON(JsonObject(mapOf("input_descriptors" to emptyArray<String>().toJsonElement())))
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
    private val wallet = randomUUID()

    @Test
    fun `given single match strategy, when calling use-case, then the result is returned`() {
        val sut = NoMatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy)
        every { credentialService.list(wallet, any()) } returns credentials
        every { matchStrategy.match(credentials, presentationDefinition) } returns vc1Filter
        val result = sut.find(wallet, presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = vc1Filter, actual = listOf(result[0]))
    }

    @Test
    fun `given multiple match strategies with same credential output, when calling use-case, then the result contains is grouped by credential`() {
        val additionalMatchStrategy = mockk<PresentationDefinitionMatchStrategy<List<FilterData>>>()
        val expectedResult = listOf(
            FilterData(
                credential = "VerifiableCredential#1",
                filters = (vc1Filter.map { it.filters } + patternFilter.map { it.filters }).flatten()
            )
        )
        val sut =
            NoMatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy, additionalMatchStrategy)
        every { credentialService.list(wallet, any()) } returns credentials
        every { matchStrategy.match(credentials, presentationDefinition) } returns vc1Filter
        every { additionalMatchStrategy.match(credentials, presentationDefinition) } returns patternFilter
        val result = sut.find(wallet, presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = expectedResult, actual = result)
    }

    @Test
    fun `given multiple match strategies with different output, when calling use-case, then the result contains both`() {
        val additionalMatchStrategy = mockk<PresentationDefinitionMatchStrategy<List<FilterData>>>()
        val sut =
            NoMatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy, additionalMatchStrategy)
        every { credentialService.list(wallet, any()) } returns credentials
        every { matchStrategy.match(credentials, presentationDefinition) } returns vc1Filter
        every { additionalMatchStrategy.match(credentials, presentationDefinition) } returns vc2Filter
        val result = sut.find(wallet, presentationDefinition)
        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = vc1Filter, actual = listOf(result[0]))
        assertEquals(expected = vc2Filter, actual = listOf(result[1]))
    }
}
