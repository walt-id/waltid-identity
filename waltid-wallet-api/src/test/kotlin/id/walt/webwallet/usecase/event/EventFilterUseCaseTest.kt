package id.walt.webwallet.usecase.event

import id.walt.webwallet.service.events.*
import id.walt.webwallet.usecase.entity.EntityNameResolutionUseCase
import id.walt.webwallet.utils.JsonUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventFilterUseCaseTest {
    private val eventServiceMock = mockk<EventService>()
    private val issuerNameResolutionMock = mockk<EntityNameResolutionUseCase>()
    private val sut = EventFilterUseCase(eventServiceMock, issuerNameResolutionMock)
    private val account = UUID()
    private val wallet = UUID()
    private val eventData = CredentialEventData(
        credentialId = "String",
        ecosystem = "String",
        logo = "String",
        type = "String",
        format = "String",
        proofType = "String",
        protocol = "String",
        subject = CredentialEventDataActor.Subject(
            subjectId = "String",
            subjectKeyType = "String",
        ),
        organization = CredentialEventDataActor.Organization.Issuer(
            did = "String",
            name = null,
            keyId = "String",
            keyType = "String",
        )
    )
    private val eventList = listOf(
        Event(
            event = "Credential",
            action = "Receive",
            tenant = "",
            account = account,
            wallet = wallet,
            data = Json.encodeToJsonElement(eventData).jsonObject
        )
    )

    @Test
    fun `test unfiltered`() = runTest {
        val filter = EventLogFilter(sortOrder = "asc", sortBy = "")
        every {
            eventServiceMock.get(
                account,
                wallet,
                filter.limit,
                any(),
                filter.sortOrder!!,
                filter.sortBy!!,
                filter.data
            )
        } returns eventList
        every { eventServiceMock.count(wallet, filter.data) } returns 1
        coEvery { issuerNameResolutionMock.resolve(wallet, "String") } returns "issuer-name"
        val result = sut.filter(account, wallet, filter)
        assertTrue(result is EventLogFilterDataResult)
        assertEquals(expected = 1, actual = result.items.size)
        assertEquals(
            expected = "String",
            actual = JsonUtils.tryGetData(result.items[0].data, "organization.did")!!.jsonPrimitive.content
        )
    }
}