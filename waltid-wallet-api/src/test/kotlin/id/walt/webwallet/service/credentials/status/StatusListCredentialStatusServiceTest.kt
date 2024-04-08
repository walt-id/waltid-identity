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
    private val credential =
        Json.decodeFromString<JsonObject>(TestUtils.loadResource("credential-status/status-list-credential/entra-missing-purpose.json"))
    private val sut = StatusListCredentialStatusService(fetchFactoryMock, validatorMock, bitParserMock)

    @Test
    fun `given a status list entry missing status-purpose, when checking status, then revocation result is returned`() =
        runTest {
            // given
            val bitValue = listOf('0')
            val statusEntry =
                Json.decodeFromString<StatusListEntry>(TestUtils.loadResource("credential-status/status-list-entry/entra-missing-purpose.json"))
            val credentialSubjectType =
                JsonUtils.tryGetData(credential, "credentialSubject.type")!!.jsonPrimitive.content
            val fetchStrategyMock = mockk<StatusListCredentialFetchStrategy>()
            every { fetchFactoryMock.new(statusEntry.statusListCredential) } returns fetchStrategyMock
            coEvery { fetchStrategyMock.fetch(statusEntry.statusListCredential) } returns credential
            every {
                validatorMock.validate(
                    entryPurpose = statusEntry.statusPurpose,
                    subjectPurpose = "revocation",
                    subjectType = credentialSubjectType,
                    credential = credential
                )
            } returns true
            every { bitParserMock.get(any(), any(), any()) } returns bitValue
            // when
            val result = sut.get(statusEntry)
            // then
            assertEquals(
                expected = CredentialStatusResult(
                    type = "revocation", result = false
                ),
                actual = result
            )
        }
    //TODO: test for statusMessage
}