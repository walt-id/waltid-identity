package id.walt.webwallet.usecase.notification

import id.walt.webwallet.db.models.Notification
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.notifications.NotificationService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.uuid.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals


class NotificationFilterUseCaseTest {
    private val credentialServiceMock = mockk<CredentialsService>()
    private val notificationServiceMock = mockk<NotificationService>()
    private val notificationFormatterMock = mockk<NotificationDataFormatter>()

    private val sut =
        NotificationFilterUseCase(notificationServiceMock, credentialServiceMock, notificationFormatterMock)
    private val notifications = listOf(
        Notification(
            id = "id#1",
            account = "account",
            wallet = "wallet",
            type = "Receive",
            status = false,
            addedOn = Clock.System.now(),
            data = Notification.CredentialIssuanceData("credential-id#1", "logo#1", "details#1")
        ),
        Notification(
            id = "id#2",
            account = "account",
            wallet = "wallet",
            type = "Receive",
            status = false,
            addedOn = Clock.System.now(),
            data = Notification.CredentialIssuanceData("credential-id#2", "logo#2", "details#2")
        ),
    )
    private val notificationDTOs =
        notifications.map { NotificationDTO(it, Json.encodeToJsonElement(it.data).jsonObject) }
    private val credentials = listOf(
        // pending
        WalletCredential(
            wallet = UUID(),
            id = "credential-id#1",
            document = "{}",
            disclosures = null,
            addedOn = Clock.System.now(),
            manifest = null,
            deletedOn = null,
            pending = true,
        ),
        // not pending
        WalletCredential(
            wallet = UUID(),
            id = "credential-id#2",
            document = "{}",
            disclosures = null,
            addedOn = Clock.System.now(),
            manifest = null,
            deletedOn = null,
            pending = false,
        ),
    )
    private val pendingFilter = NotificationFilterParameter(
        type = "Receive", isRead = null, sort = "desc", addedOn = null, showPending = true
    )
    private val notPendingFilter = NotificationFilterParameter(
        type = "Receive", isRead = null, sort = "desc", addedOn = null, showPending = false
    )

    @BeforeTest
    fun setup() {
        every { notificationServiceMock.list(any(), any(), any(), any(), any()) } returns notifications
        every { credentialServiceMock.get(any()) } returns credentials
        coEvery { notificationFormatterMock.format(notifications[0]) } returns notificationDTOs[0]
        coEvery { notificationFormatterMock.format(notifications[1]) } returns notificationDTOs[1]
    }

    @Test
    fun `filter pending credentials`() = runTest {
        val result = sut.filter(UUID(), pendingFilter)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = notificationDTOs[0], actual = result[0])
    }

    @Test
    fun `filter non-pending credentials`() = runTest {
        val result = sut.filter(UUID(), notPendingFilter)
        assertEquals(expected = 1, actual = result.size)
        assertEquals(expected = notificationDTOs[1], actual = result[0])
    }
}