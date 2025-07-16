@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.exchange

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.UuidUtils.randomUUID
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.usecase.exchange.strategies.PresentationDefinitionMatchStrategy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

class MatchPresentationDefinitionCredentialsUseCaseTest {

    private val credentialService = mockk<CredentialsService>()
    private val matchStrategy = mockk<PresentationDefinitionMatchStrategy<List<WalletCredential>>>()
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
    val wallet = randomUUID()

    @Test
    fun `full match`() {
        val sut = MatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy)
        every { credentialService.list(wallet, any()) } returns credentials
        every { matchStrategy.match(credentials, presentationDefinition) } returns credentials
        val result = sut.match(wallet, presentationDefinition)
        assertEquals(expected = 2, result.size)
        assertEquals(expected = credentials[0], result[0])
        assertEquals(expected = credentials[1], result[1])
    }

    @Test
    fun `partial match`() {
        val sut = MatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy)
        val credentialList = listOf(credentials[0])
        every { credentialService.list(wallet, any()) } returns credentialList
        every { matchStrategy.match(credentialList, presentationDefinition) } returns credentialList
        val result = sut.match(wallet, presentationDefinition)
        assertEquals(expected = 1, result.size)
        assertEquals(expected = credentials[0], result[0])
    }

    @Test
    fun `no match credentials`() {
        val sut = MatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy)
        every { credentialService.list(wallet, any()) } returns credentials
        every { matchStrategy.match(credentials, presentationDefinition) } returns emptyList()
        val result = sut.match(wallet, presentationDefinition)
        assertEquals(expected = 0, result.size)
    }

    @Test
    fun `no match no credentials`() {
        val sut = MatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy)
        every { credentialService.list(wallet, any()) } returns emptyList()
        every { matchStrategy.match(emptyList(), presentationDefinition) } returns emptyList()
        val result = sut.match(wallet, presentationDefinition)
        assertEquals(expected = 0, result.size)
    }

    @Test
    fun `given a list of match strategies, when 1st matches then result is returned`() {
        val anotherMatchStrategy = mockk<PresentationDefinitionMatchStrategy<List<WalletCredential>>>()
        val sut = MatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy, anotherMatchStrategy)
        val credentialList = listOf(credentials[0])
        every { credentialService.list(wallet, any()) } returns credentialList
        every { matchStrategy.match(credentialList, presentationDefinition) } returns credentialList
        every { anotherMatchStrategy.match(credentialList, presentationDefinition) } returns credentialList
        val result = sut.match(wallet, presentationDefinition)
        assertEquals(expected = 1, result.size)
        assertEquals(expected = credentials[0], result[0])
        verify(exactly = 1) { matchStrategy.match(credentialList, presentationDefinition) }
        verify(exactly = 0) { anotherMatchStrategy.match(credentialList, presentationDefinition) }
    }

    @Test
    fun `given a list of match strategies, when 1st returns no match then 2nd is returned`() {
        val anotherMatchStrategy = mockk<PresentationDefinitionMatchStrategy<List<WalletCredential>>>()
        val sut = MatchPresentationDefinitionCredentialsUseCase(credentialService, matchStrategy, anotherMatchStrategy)
        val credentialList = listOf(credentials[0])
        every { credentialService.list(wallet, any()) } returns credentialList
        every { matchStrategy.match(credentialList, presentationDefinition) } returns emptyList()
        every { anotherMatchStrategy.match(credentialList, presentationDefinition) } returns credentialList
        val result = sut.match(wallet, presentationDefinition)
        assertEquals(expected = 1, result.size)
        assertEquals(expected = credentials[0], result[0])
        verifySequence {
            matchStrategy.match(credentialList, presentationDefinition)
            anotherMatchStrategy.match(credentialList, presentationDefinition)
        }
    }
}
