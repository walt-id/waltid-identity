package id.walt.webwallet.usecase.claim

import TestUtils
import id.walt.webwallet.seeker.Seeker
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.events.CredentialEventData
import id.walt.webwallet.service.events.CredentialEventDataActor
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.service.trust.TrustValidationService
import id.walt.webwallet.usecase.entity.EntityNameResolutionUseCase
import id.walt.webwallet.usecase.event.EventLogUseCase
import id.walt.webwallet.usecase.issuer.IssuerUseCase
import id.walt.webwallet.usecase.notification.NotificationDispatchUseCase
import id.walt.webwallet.usecase.notification.NotificationUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SilentClaimStrategyTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val issuanceService = mockk<IssuanceService>()
    private val credentialService = mockk<CredentialsService>()
    private val issuerTrustValidationService = mockk<TrustValidationService>()
    private val issuerNameResolutionUseCase = mockk<EntityNameResolutionUseCase>()
    private val issuerUseCase = mockk<IssuerUseCase>()
    private val eventUseCase = mockk<EventLogUseCase>()
    private val notificationUseCase = mockk<NotificationUseCase>()
    private val notificationDispatchUseCaseMock = mockk<NotificationDispatchUseCase>()
    private val credentialTypeSeeker = mockk<Seeker<String>>()
    private val didService: DidsService = mockk<DidsService>()
    private val accountService: AccountsService = mockk<AccountsService>()
    private val sut = SilentClaimStrategy(
        issuanceService = issuanceService,
        credentialService = credentialService,
        issuerTrustValidationService = issuerTrustValidationService,
        accountService = accountService,
        didService = didService,
        issuerUseCase = issuerUseCase,
        eventUseCase = eventUseCase,
        notificationUseCase = notificationUseCase,
        notificationDispatchUseCase = notificationDispatchUseCaseMock,
        credentialTypeSeeker = credentialTypeSeeker
    )
    private val did = "did:my:test"
    private val offer = "openid-credential-offer://my-offer-uri"
    private val account = UUID("bd698a0f-1703-4565-aab3-747c374152dd")
    private val wallet = UUID("bd698a0f-1703-4565-aab3-747c374152dd")
    private val issuerData = IssuerDataTransferObject(
        wallet = wallet,
        did = "name",
        uiEndpoint = "uiEndpoint",
    )
    private val credentialType = "test-credential-type"
    private val credentialId = "http://example.gov/credentials/1"
    private val credentialData =
        json.decodeFromString<IssuanceService.CredentialDataResult>(TestUtils.loadResource("silent-claiming/credential-data.json"))
    private val eventData =
        json.decodeFromString<CredentialEventData>(TestUtils.loadResource("silent-claiming/event-data.json"))

    @BeforeTest
    fun setup() {
        every { accountService.getAccountForWallet(wallet) } returns account
        every { didService.getWalletsForDid(any()) } returns listOf(wallet)
        every { credentialTypeSeeker.get(any()) } returns credentialType
        coEvery { issuanceService.useOfferRequest(any(), any(), any()) } returns listOf(credentialData)
        coEvery { issuerUseCase.get(wallet, any()) } returns Result.success(issuerData)
        every { credentialService.add(wallet = any(), any()) } returns listOf(credentialId)
        every { eventUseCase.credentialEventData(any(), any(), any(), any()) } returns eventData
        every { eventUseCase.subjectData(any()) } returns CredentialEventDataActor.Subject(
            "subjectId",
            "subjectKeyType"
        )
        every { eventUseCase.issuerData(any()) } returns CredentialEventDataActor.Organization.Issuer(
            "did",
            "name",
            "keyId",
            "keyType",
        )
        every { eventUseCase.log(any()) } just Runs
        every { notificationUseCase.add(any()) } returns listOf(UUID.generateUUID())
        coEvery { notificationDispatchUseCaseMock.send(any()) } just Runs
        coEvery { issuerNameResolutionUseCase.resolve(any()) } returns "test"
    }

    @Test
    fun `given a trusted issuer, when claiming the offer, then an event is logged and a notification created and sent`() =
        runTest {
            coEvery { issuerTrustValidationService.validate(any(), any(), any()) } returns true

            val result = sut.claim(did, offer)

            assertEquals(1, result.size)
            assertEquals(credentialId, result[0])
            verify(exactly = 1) { eventUseCase.log(any()) }
            verify(exactly = 1) { notificationUseCase.add(any()) }
            coVerify(exactly = 1) { notificationDispatchUseCaseMock.send(any()) }
        }

    @Test
    fun `given an untrusted issuer, when claiming the offer, then no event is logged and no notification created and sent`() =
        runTest {
            coEvery { issuerTrustValidationService.validate(any(), any(), any()) } returns false

            val result = sut.claim(did, offer)

            assertEquals(0, result.size)
            verify(exactly = 0) { eventUseCase.log(any()) }
            verify(exactly = 0) { notificationUseCase.add(any()) }
            coVerify(exactly = 0) { notificationDispatchUseCaseMock.send(any()) }
        }
}
