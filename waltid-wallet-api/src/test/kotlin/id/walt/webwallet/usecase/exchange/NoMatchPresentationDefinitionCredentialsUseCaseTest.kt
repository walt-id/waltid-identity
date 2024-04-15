package id.walt.webwallet.usecase.exchange

import TestUtils
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.usecase.exchange.strategies.PresentationDefinitionMatchStrategy
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class NoMatchPresentationDefinitionCredentialsUseCaseTest {

    private val credentialService = mockk<CredentialsService>()
    private val matchStrategy = mockk<PresentationDefinitionMatchStrategy<List<TypeFilter>>>()
    private val filters =
        Json.decodeFromString<List<List<TypeFilter>>>(TestUtils.loadResource("presentation-definition/filters.json"))
    private val presentationDefinition =
        PresentationDefinition.fromJSON(JsonObject(mapOf("input_descriptors" to emptyArray<String>().toJsonElement())))
    private val credentials = listOf(
        WalletCredential(
            wallet = UUID(),
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
        ),
    )
    private val wallet = UUID()

    @Test
    fun `given single match strategy, when calling use-case, then the result is returned`() {
        val sut = NoMatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy)
        every { credentialService.list(wallet, any()) } returns credentials
        every { matchStrategy.match(credentials, presentationDefinition) } returns filters[0]
        val result = sut.find(wallet, presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = filters[0], actual = listOf(result[0]))
    }

    @Test
    fun `given multiple match strategies with same output, when calling use-case, then the result contains no duplicates`() {
        val additionalMatchStrategy = mockk<PresentationDefinitionMatchStrategy<List<TypeFilter>>>()
        val sut = NoMatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy)
        every { credentialService.list(wallet, any()) } returns credentials
        every { matchStrategy.match(credentials, presentationDefinition) } returns filters[0]
        every { additionalMatchStrategy.match(credentials, presentationDefinition) } returns filters[0]
        val result = sut.find(wallet, presentationDefinition)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = filters[0], actual = listOf(result[0]))
    }

    @Test
    fun `given multiple match strategies with different output, when calling use-case, then the result contains both`() {
        val additionalMatchStrategy = mockk<PresentationDefinitionMatchStrategy<List<TypeFilter>>>()
        val sut =
            NoMatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy, additionalMatchStrategy)
        every { credentialService.list(wallet, any()) } returns credentials
        every { matchStrategy.match(credentials, presentationDefinition) } returns filters[0]
        every { additionalMatchStrategy.match(credentials, presentationDefinition) } returns filters[1]
        val result = sut.find(wallet, presentationDefinition)
        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = filters[0], actual = listOf(result[0]))
        assertEquals(expected = filters[1], actual = listOf(result[1]))
    }
}