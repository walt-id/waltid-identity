@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.event

import id.walt.crypto.utils.UuidUtils.randomUUID
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class EventFilterUseCaseTest {
    private val eventServiceMock = mockk<EventService>()
    private val issuerNameResolutionMock = mockk<EntityNameResolutionUseCase>()
    private val verifierNameResolutionMock = mockk<EntityNameResolutionUseCase>()
    private val sut = EventFilterUseCase(eventServiceMock, issuerNameResolutionMock, verifierNameResolutionMock)
    private val account = randomUUID()
    private val wallet = randomUUID()
    private val issuerData = CredentialEventDataActor.Organization.Issuer(
        did = "String",
        name = null,
        keyId = "String",
        keyType = "String",
    )
    private val verifierData = CredentialEventDataActor.Organization.Verifier(
        did = "String",
        name = null,
        policies = emptyList()
    )
    private val noFilter = EventLogFilter(sortOrder = "asc", sortBy = "")

    @Test
    fun `given an issuance event, when listing events, then result contains the issuer resolved name`() = runTest {
        every {
            eventServiceMock.get(
                account,
                wallet,
                noFilter.limit,
                any(),
                noFilter.sortOrder!!,
                noFilter.sortBy!!,
                noFilter.data
            )
        } returns listOf(getEvent(EventType.Credential.Receive, issuerData))
        every { eventServiceMock.count(wallet, noFilter.data) } returns 1
        coEvery { issuerNameResolutionMock.resolve("String") } returns "issuer-name"
        val result = sut.filter(account, wallet, noFilter)
        assertTrue(result is EventLogFilterDataResult)
        assertEquals(expected = 1, actual = result.items.size)
        assertEquals(
            expected = "issuer-name",
            actual = JsonUtils.tryGetData(result.items[0].data, "organization.name")!!.jsonPrimitive.content
        )
    }

    @Test
    fun `given a presentation event, when listing events, then result contains the verifier resolved name`() = runTest {
        every {
            eventServiceMock.get(
                account,
                wallet,
                noFilter.limit,
                any(),
                noFilter.sortOrder!!,
                noFilter.sortBy!!,
                noFilter.data
            )
        } returns listOf(getEvent(EventType.Credential.Present, verifierData))
        every { eventServiceMock.count(wallet, noFilter.data) } returns 1
        coEvery { verifierNameResolutionMock.resolve("String") } returns "verifier-name"
        val result = sut.filter(account, wallet, noFilter)
        assertTrue(result is EventLogFilterDataResult)
        assertEquals(expected = 1, actual = result.items.size)
        assertEquals(
            expected = "verifier-name",
            actual = JsonUtils.tryGetData(result.items[0].data, "organization.name")!!.jsonPrimitive.content
        )
    }

    private fun getEvent(action: EventType.Action, organization: CredentialEventDataActor.Organization) =
        CredentialEventData(
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
            organization = organization
        ).let {
            Event(
                event = action.type,
                action = action.toString(),
                tenant = "",
                account = account,
                wallet = wallet,
                data = Json.encodeToJsonElement(it).jsonObject
            )
        }
}
