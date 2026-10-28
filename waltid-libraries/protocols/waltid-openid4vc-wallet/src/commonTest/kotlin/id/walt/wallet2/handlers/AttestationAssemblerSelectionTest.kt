package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.EncodedKey
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class AttestationAssemblerSelectionTest {
    private val attest = ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH
    private val assembler = ClientAttestationAssembler(object : WalletAttestationProvider {
        override suspend fun getAttestationJwt(instancePublicKeyJwk: EncodedKey.Jwk, clientId: String) = "jwt"
        override suspend fun getAttestationJwt(instanceKey: Key, clientId: String) = "jwt"
    })

    @Test
    fun `fails closed when attestation is the only token method and no provider is configured`() {
        val error = assertFailsWith<WalletInstanceAttestationRequiredException> {
            attestationAssemblerForTokenMethods(setOf(attest), null)
        }
        assertEquals(
            "This issuer requires a wallet instance attestation, and no wallet provider is configured",
            error.message,
        )
    }

    @Test
    fun `allows an anonymous pre-authorized grant without a provider`() {
        assertNull(
            attestationAssemblerForTokenMethods(
                setOf(attest),
                null,
                anonymousPreAuthorizedAccessSupported = true,
            ),
        )
    }

    @Test
    fun `leaves another token method available when attestation is optional`() {
        assertNull(attestationAssemblerForTokenMethods(setOf(attest, "client_secret_post"), null))
    }

    @Test
    fun `ignores a provider when the issuer does not advertise attestation`() {
        assertNull(attestationAssemblerForTokenMethods(setOf("none"), assembler))
        assertNull(attestationAssemblerForTokenMethods(null, null))
    }

    @Test
    fun `uses the configured provider when the issuer requires attestation`() {
        assertSame(assembler, attestationAssemblerForTokenMethods(setOf(attest), assembler))
    }
}
