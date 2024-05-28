package id.walt.webwallet.service.credentials.status

import TestUtils
import id.walt.webwallet.service.BitStringValueParser
import id.walt.webwallet.service.credentials.CredentialValidator
import id.walt.webwallet.service.credentials.status.fetch.StatusListCredentialFetchFactory
import id.walt.webwallet.service.credentials.status.fetch.StatusListCredentialFetchStrategy
import id.walt.webwallet.usecase.credential.CredentialStatusResult
import id.walt.webwallet.utils.JsonUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusListCredentialStatusServiceTest {
    private val fetchFactoryMock = mockk<StatusListCredentialFetchFactory>()
    private val validatorMock = mockk<CredentialValidator>()
    private val bitParserMock = mockk<BitStringValueParser>()
    private val fetchStrategyMock = mockk<StatusListCredentialFetchStrategy>()
    private val sut = StatusListCredentialStatusService(fetchFactoryMock, validatorMock, bitParserMock)

    @Test
    fun `given a status list entry missing status-purpose, when checking status, then revocation result is returned`() =
        runTest {
            // given
            val statusEntry =
                Json.decodeFromString<StatusListEntry>(TestUtils.loadResource("credential-status/status-list-entry/entra-missing-purpose.json"))
            val credential =
                Json.decodeFromString<JsonObject>(TestUtils.loadResource("credential-status/status-list-credential/entra-missing-purpose.json"))
            val subjectType =
                JsonUtils.tryGetData(credential, "credentialSubject.type")!!.jsonPrimitive.content
            val bitValue = listOf('0')
            every { bitParserMock.get(any(), any(), any()) } returns bitValue
            every { fetchFactoryMock.new(statusEntry.statusListCredential) } returns fetchStrategyMock
            coEvery { fetchStrategyMock.fetch(statusEntry.statusListCredential) } returns credential
            every {
                validatorMock.validate(
                    entryPurpose = statusEntry.statusPurpose,
                    subjectPurpose = "revocation",
                    subjectType = subjectType,
                    credential = credential
                )
            } returns true
            // when
            val result = sut.get(statusEntry)
            // then
            assertEquals(
                expected = CredentialStatusResult(type = "revocation", result = false, message = "unset"),
                actual = result
            )
        }

    @Test
    fun `given a status list entry having bitSize greater than 1 and statusMessage, when checking status, then revocation result is returned with status message`() =
        runTest {
            // given
            val bitValue = listOf('1', '0')
            val statusEntry =
                Json.decodeFromString<StatusListEntry>(TestUtils.loadResource("credential-status/status-list-entry/entra-with-status-message.json"))
            val credential =
                Json.decodeFromString<JsonObject>(TestUtils.loadResource("credential-status/status-list-credential/entra-with-status-message.json"))
            val subjectType =
                JsonUtils.tryGetData(credential, "credentialSubject.type")!!.jsonPrimitive.content
            every { bitParserMock.get(any(), any(), any()) } returns bitValue
            every { fetchFactoryMock.new(statusEntry.statusListCredential) } returns fetchStrategyMock
            coEvery { fetchStrategyMock.fetch(statusEntry.statusListCredential) } returns credential
            every {
                validatorMock.validate(
                    entryPurpose = statusEntry.statusPurpose,
                    subjectPurpose = "status",
                    subjectType = subjectType,
                    credential = credential
                )
            } returns true
            // when
            val result = sut.get(statusEntry)
            // then
            assertEquals(
                expected = CredentialStatusResult(type = "status", result = true, message = "pending_review"),
                actual = result
            )
        }
}