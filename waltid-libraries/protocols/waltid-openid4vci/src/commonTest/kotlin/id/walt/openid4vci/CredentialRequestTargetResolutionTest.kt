package id.walt.openid4vci

import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.requests.credential.CredentialRequestTargetResolution
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.requests.credential.resolveCredentialConfigurationId
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
