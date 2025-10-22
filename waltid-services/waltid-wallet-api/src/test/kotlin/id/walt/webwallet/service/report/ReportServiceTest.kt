@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.report

import TestUtils
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.Event
import id.walt.webwallet.service.events.EventService
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ReportServiceTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val eventServiceMock: EventService = mockk()
    private val credentialServiceMock: CredentialsService = mockk()
    private val sut = ReportService.Credentials(credentialServiceMock, eventServiceMock)

    @Test
    fun frequent() {
        val events =
            json.decodeFromString<List<Event>>(TestUtils.loadResource("frequently-used/presentation-events.json"))
        val credentials =
            json.decodeFromString<List<WalletCredential>>(TestUtils.loadResource("frequently-used/credentials.json"))
        every { eventServiceMock.get(any(), any(), any(), any(), any(), any(), any()) } returns events
        every {
            credentialServiceMock.get(
                any(),
                listOf(
                    "http://example.gov/credentials/3",
                    "http://example.gov/credentials/2",
                    "http://example.gov/credentials/1"
                )
            )
        } returns credentials
        val result = sut.frequent(
            CredentialReportRequestParameter(walletId = Uuid.parse("bd698a0f-1703-4565-aab3-747c374152dd"), limit = -1)
        )
        assertEquals(3, result.size)
        assertEquals("http://example.gov/credentials/3", result[0].id)
        assertEquals("http://example.gov/credentials/2", result[1].id)
        assertEquals("http://example.gov/credentials/1", result[2].id)
    }
}
