@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.DcApiAnnexDFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VerificationSessionCreatorClientIdTest {

    private fun unsignedCrossDevice(sessionId: String? = "session-1") = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            sessionId = sessionId,
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                )
            )
        )
    )

    @Test
    fun `omitted clientId becomes redirect_uri bound to response_uri`() = runTest {
        val urlPrefix = "https://verifier.example.com/verification-session"
        val session = VerificationSessionCreator.createVerificationSession(
            setup = unsignedCrossDevice(),
            clientId = null,
            urlPrefix = urlPrefix,
            urlHost = "openid4vp://authorize",
        )

        val expected = "redirect_uri:$urlPrefix/${session.id}/response"
        assertEquals("$urlPrefix/${session.id}/response", session.authorizationRequest.responseUri)
        assertEquals(expected, session.authorizationRequest.clientId)
        assertEquals(expected, session.bootstrapAuthorizationRequest?.clientId)
    }

    @Test
    fun `blank clientId is treated as omitted`() = runTest {
        val urlPrefix = "https://verifier.example.com/verification-session"
        val session = VerificationSessionCreator.createVerificationSession(
            setup = unsignedCrossDevice("session-blank"),
            clientId = "  ",
            urlPrefix = urlPrefix,
            urlHost = "openid4vp://authorize",
        )

        assertEquals(
            "redirect_uri:$urlPrefix/session-blank/response",
            session.authorizationRequest.clientId,
        )
    }

    @Test
    fun `signable clientId on unsigned session becomes redirect_uri`() = runTest {
        val urlPrefix = "https://verifier.example.com/verification-session"
        val session = VerificationSessionCreator.createVerificationSession(
            setup = unsignedCrossDevice(),
            clientId = "x509_san_dns:verifier.example.com",
            urlPrefix = urlPrefix,
            urlHost = "openid4vp://authorize",
        )

        val expected = "redirect_uri:$urlPrefix/${session.id}/response"
        assertEquals(expected, session.authorizationRequest.clientId)
        assertEquals(expected, session.bootstrapAuthorizationRequest?.clientId)
    }

    @Test
    fun `explicit redirect_uri clientId on unsigned session is preserved`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            setup = unsignedCrossDevice(),
            clientId = "redirect_uri:https://verifier.example/response",
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
        )

        assertEquals("redirect_uri:https://verifier.example/response", session.authorizationRequest.clientId)
        assertEquals("redirect_uri:https://verifier.example/response", session.bootstrapAuthorizationRequest?.clientId)
    }

    @Test
    fun `signed request preserves explicit signable clientId`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    signedRequest = true,
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                        )
                    )
                )
            ),
            clientId = "x509_san_dns:verifier.example.com",
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
            key = key,
        )

        assertEquals("x509_san_dns:verifier.example.com", session.authorizationRequest.clientId)
        assertEquals("x509_san_dns:verifier.example.com", session.bootstrapAuthorizationRequest?.clientId)
    }

    @Test
    fun `signed request without clientId is rejected`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val error = assertFailsWith<IllegalArgumentException> {
            VerificationSessionCreator.createVerificationSession(
                setup = CrossDeviceFlowSetup(
                    core = GeneralFlowConfig(
                        signedRequest = true,
                        dcqlQuery = DcqlQuery(
                            credentials = listOf(
                                CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                            )
                        )
                    )
                ),
                clientId = null,
                urlPrefix = "https://verifier.example.com/verification-session",
                urlHost = "openid4vp://authorize",
                key = key,
            )
        }
        assertEquals(
            "Signed requests require a client_id; omitting client_id only auto-generates the unsigned redirect_uri scheme",
            error.message,
        )
    }

    @Test
    fun `signed request with explicit redirect_uri clientId is rejected`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val error = assertFailsWith<IllegalArgumentException> {
            VerificationSessionCreator.createVerificationSession(
                setup = CrossDeviceFlowSetup(
                    core = GeneralFlowConfig(
                        signedRequest = true,
                        dcqlQuery = DcqlQuery(
                            credentials = listOf(
                                CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                            )
                        )
                    )
                ),
                clientId = "redirect_uri:https://verifier.example/response",
                urlPrefix = "https://verifier.example.com/verification-session",
                urlHost = "openid4vp://authorize",
                key = key,
            )
        }
        assertEquals(
            "Signed requests cannot use the redirect_uri client_id prefix",
            error.message,
        )
    }

    @Test
    fun `unsigned DC API still omits clientId`() = runTest {
        val setup = DcApiAnnexDFlowSetup.EX_UNSIGNED_UNENCRYPTED_MDL.copy(
            core = DcApiAnnexDFlowSetup.EX_UNSIGNED_UNENCRYPTED_MDL.core.copy(clientId = null),
        )
        val session = VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = null,
            urlPrefix = null,
            urlHost = setup.expectedOrigins.first(),
        )

        assertNull(session.authorizationRequest.clientId)
        assertNull(session.bootstrapAuthorizationRequest)
        assertNotNull(session.authorizationRequest.dcqlQuery)
    }
}
