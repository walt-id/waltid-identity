package id.walt.openid4vci.clientauth.attestation.verifier

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class X509ChainClientAttestationVerifierPkixTest {

    private val verifierTest = X509ChainClientAttestationVerifierTest()

    @Test
    fun `authenticates client attestation backed by trusted x509 chain`() = runTest {
        verifierTest.verifiesTrustedClientAttestation()
    }

    @Test
    fun `rejects client attestation backed by untrusted x509 chain`() = runTest {
        verifierTest.rejectsUntrustedClientAttestation()
    }
}
