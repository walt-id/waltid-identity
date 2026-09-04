package id.walt.openid4vci

import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.requests.credential.CredentialRequestTargetResolution
import id.walt.openid4vci.requests.credential.CredentialAuthorization
import id.walt.openid4vci.requests.credential.CredentialAuthorizationResolution
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.requests.credential.resolveCredentialAuthorization
import id.walt.openid4vci.requests.credential.resolveCredentialConfigurationId
import id.walt.openid4vci.requests.credential.toAuthorizationDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialRequestTargetResolutionTest {

    @Test
    fun `unknown credential configuration resolves to final credential error`() {
        val resolution = credentialRequest(credentialConfigurationId = "unknown")
            .resolveCredentialConfigurationId(
                credentialConfigurationExists = { it == "known" },
                resolveCredentialIdentifier = { null },
            )

        assertTrue(resolution is CredentialRequestTargetResolution.Failure)
        assertEquals(CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION, resolution.error.error)
    }

    @Test
    fun `unknown credential identifier resolves to final credential error`() {
        val resolution = credentialRequest(credentialIdentifier = "unknown")
            .resolveCredentialConfigurationId(
                credentialConfigurationExists = { it == "known" },
                resolveCredentialIdentifier = { null },
            )

        assertTrue(resolution is CredentialRequestTargetResolution.Failure)
        assertEquals(CredentialErrorCodes.UNKNOWN_CREDENTIAL_IDENTIFIER, resolution.error.error)
    }

    @Test
    fun `known credential identifier resolves to mapped credential configuration`() {
        val resolution = credentialRequest(credentialIdentifier = "identifier")
            .resolveCredentialConfigurationId(
                credentialConfigurationExists = { it == "known" },
                resolveCredentialIdentifier = { "known" },
            )

        assertTrue(resolution is CredentialRequestTargetResolution.Success)
        assertEquals("known", resolution.credentialConfigurationId)
    }

    @Test
    fun `credential identifier resolves exact authorization`() {
        val resolution = credentialRequest(credentialIdentifier = "credential-2")
            .resolveCredentialAuthorization(
                listOf(
                    CredentialAuthorization("credential-1", "configuration"),
                    CredentialAuthorization("credential-2", "configuration"),
                )
            )

        assertTrue(resolution is CredentialAuthorizationResolution.Success)
        assertEquals("credential-2", resolution.authorization.credentialIdentifier)
    }

    @Test
    fun `configuration id is rejected when multiple credentials share it`() {
        val resolution = credentialRequest(credentialConfigurationId = "configuration")
            .resolveCredentialAuthorization(
                listOf(
                    CredentialAuthorization("credential-1", "configuration"),
                    CredentialAuthorization("credential-2", "configuration"),
                )
            )

        assertTrue(resolution is CredentialAuthorizationResolution.Failure)
        assertEquals(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, resolution.error.error)
    }

    @Test
    fun `authorization details group identifiers by configuration in encounter order`() {
        val details = listOf(
            CredentialAuthorization("credential-1", "configuration-a"),
            CredentialAuthorization("credential-2", "configuration-b"),
            CredentialAuthorization("credential-3", "configuration-a"),
        ).toAuthorizationDetails()

        assertEquals(listOf("configuration-a", "configuration-b"), details.map { it.credentialConfigurationId })
        assertEquals(listOf("credential-1", "credential-3"), details.first().credentialIdentifiers)
        assertEquals(listOf("credential-2"), details.last().credentialIdentifiers)
    }

    private fun credentialRequest(
        credentialConfigurationId: String? = null,
        credentialIdentifier: String? = null,
    ) = DefaultCredentialRequest(
        client = DefaultClient(
            id = "wallet",
            redirectUris = emptyList(),
            grantTypes = emptySet(),
            responseTypes = emptySet(),
        ),
        credentialIdentifier = credentialIdentifier,
        credentialConfigurationId = credentialConfigurationId,
        proofs = null,
        credentialResponseEncryption = null,
        requestForm = emptyMap(),
        session = null,
    )
}
