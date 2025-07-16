@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.credential

import id.walt.crypto.utils.UuidUtils.randomUUID
import id.walt.oid4vc.data.CredentialFormat
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialStatusServiceFactory
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.credentials.status.CredentialStatusService
import id.walt.webwallet.service.credentials.status.StatusListEntry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class CredentialStatusUseCaseTest {

    private val credentialServiceMock = mockk<CredentialsService>()
    private val statusFactoryMock = mockk<CredentialStatusServiceFactory>()
    private val sut = CredentialStatusUseCase(credentialServiceMock, statusFactoryMock)
    private val wallet = randomUUID()
    private val credentialId = "credential-id"
    private val credentialSingleStatus = WalletCredential(
        wallet = wallet,
        id = credentialId,
        document = """
        {
        "type":
        [
        "VerifiableCredential#1"
        ],
        "credentialStatus":
        {
        "id": "https://example.com/credentials/status/3#94567",
        "type": "RevocationList2021Status",
        "statusPurpose": "revocation",
        "statusListIndex": "94567",
        "statusListCredential": "https://example.com/credentials/status/3"
        }
        }
        """.trimIndent(),
        disclosures = null,
        addedOn = Clock.System.now(),
        deletedOn = null,
        format = CredentialFormat.ldp_vc
    )
    @OptIn(ExperimentalUuidApi::class)
    private val credentialMultiStatus = WalletCredential(
        wallet = wallet,
        id = credentialId,
        document = """
        {
        "type":
        [
            "VerifiableCredential#1"
        ],
        "credentialStatus":
        [
        {
        "id": "https://example.com/credentials/status/3#94567",
        "type": "RevocationList2021Status",
        "statusPurpose": "revocation",
        "statusListIndex": "94567",
        "statusListCredential": "https://example.com/credentials/status/3"
        },
        {
        "id": "https://example.com/credentials/status/4#23452",
        "type": "RevocationList2021Status",
        "statusPurpose": "suspension",
        "statusListIndex": "23452",
        "statusListCredential": "https://example.com/credentials/status/4"
        }
        ]
        }
        """.trimIndent(),
        disclosures = null,
        addedOn = Clock.System.now(),
        deletedOn = null,
        format = CredentialFormat.ldp_vc
    )

    @Test
    fun `given a credential having single status-list credential status, when getting status, then returns the status result`() =
        runTest {
            val service = mockk<CredentialStatusService>()
            val status = CredentialStatusResult("revocation", false, message = "unset")
            every { statusFactoryMock.new(any()) } returns service
            coEvery { service.get(any()) } returns status
            every { credentialServiceMock.get(wallet, credentialId) } returns credentialSingleStatus
            val result = sut.get(wallet, credentialId)
            assertEquals(expected = listOf(status), actual = result)
        }

    @Test
    fun `given a credential having multiple status-list credential status entries, when getting status, then returns the status result for each entry`() =
        runTest {
            val service = mockk<CredentialStatusService>()
            val statusListEntryRevocation = StatusListEntry(
                id = "https://example.com/credentials/status/3#94567",
                statusPurposeOptional = "revocation",
                type = "RevocationList2021Status",
                statusListIndex = 94567UL,
                statusListCredential = "https://example.com/credentials/status/3",
            )
            val statusListEntrySuspension = StatusListEntry(
                id = "https://example.com/credentials/status/4#23452",
                type = "RevocationList2021Status",
                statusPurposeOptional = "suspension",
                statusListIndex = 23452UL,
                statusListCredential = "https://example.com/credentials/status/4",
            )
            val statusRevocation = CredentialStatusResult("revocation", false, message = "unset")
            val statusSuspension = CredentialStatusResult("suspension", false, message = "unset")
            every { statusFactoryMock.new(any()) } returns service
            coEvery { service.get(statusListEntryRevocation) } returns statusRevocation
            coEvery { service.get(statusListEntrySuspension) } returns statusSuspension
            every { credentialServiceMock.get(wallet, credentialId) } returns credentialMultiStatus
            val result = sut.get(wallet, credentialId)
            assertEquals(expected = listOf(statusRevocation, statusSuspension), actual = result)
        }
}
