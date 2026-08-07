package id.walt.x509

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * DEMONSTRATIVE - remove before merging. Documents why `mso_mdoc/issuer_auth` rejects mDLs from the
 * deployed public demo issuer, using the certificate that deployment actually serves.
 *
 * ## Provenance
 *
 * Captured from a real presentation: an mDL issued by `issuer2.demo.walt.id` was presented over the
 * DC API and the accepting verifier (`verifier2.demo.walt.id`, `0.23.0`) reported this chain in its
 * `mso_mdoc/issuer_auth` result under `certificate_chain`. A public demo document signer, no secret.
 *
 * ## Why it matters
 *
 * The same presentation is rejected by `verifier2.portal.test.waltid.cloud`
 * (`1.0.2608051216-feat-portal2-dcapi`) with
 * `Failed VP policies: mdl/mso_mdoc/issuer_auth`, while `verifier2.demo.walt.id` accepts it - same
 * wallet, same issuer, same bytes. So the difference is the verifier's DS-profile enforcement, and
 * the certificate below is what any conformant check has to reject.
 *
 * It is an X.509 **version 1** certificate: it carries no extensions whatsoever, so it has no
 * `keyUsage` and no `extendedKeyUsage`. ISO 18013-5 requires a document signer to carry
 * `keyUsage:digitalSignature` and EKU `1.0.18013.5.1.2` (mdlDS). Note the asymmetry this creates in
 * [validateDocumentSigningCertificateUsage]: absent `keyUsage` fails closed (`canSignData` is false
 * when the extension is missing), while absent `extendedKeyUsage` is skipped entirely because the
 * EKU check is inside `extendedKeyUsageOids?.let { }`. A v1 certificate is therefore rejected for
 * the first reason only - it never reaches the EKU rule.
 *
 * The fix is on the issuer, not in the wallet or in this validator: reissue the demo document signer
 * as a v3 certificate with the ISO 18013-5 usage extensions.
 */
class DeployedIssuerDocumentSignerTest {

    @Test
    fun deployedDemoIssuerDocumentSignerIsVersion1WithNoUsageExtensions() {
        val certificate = PlatformX509Certificate.parse(deployedDemoDocumentSigner)

        // Both are absent because there are no extensions at all, not because they are empty.
        assertFalse(
            certificate.canSignData,
            "Deployed demo document signer unexpectedly declares keyUsage:digitalSignature",
        )
        assertNull(
            certificate.extendedKeyUsageOids,
            "Deployed demo document signer unexpectedly declares an extendedKeyUsage",
        )
        assertFalse(certificate.isCertificateAuthority)
        assertTrue(certificate.criticalExtensionOids.isEmpty())
    }

    @Test
    fun documentSignerValidationRejectsItForMissingDigitalSignature() {
        // Well inside the certificate's own validity window (2025-05-14 .. 2075-05-02), so validity
        // cannot be the reason it is rejected.
        val withinValidity = Instant.parse("2026-08-08T00:00:00Z")

        val failure = assertFailsWith<IllegalArgumentException> {
            deployedDemoDocumentSigner.validateDocumentSigningCertificateUsage(withinValidity)
        }
        assertEquals(
            "Document signer certificate must permit digitalSignature",
            failure.message,
            "Expected the keyUsage rule to be the rejection reason",
        )
    }

    private companion object {
        /**
         * `C=AT, ST=Vienna, L=Vienna, O=walt.id, OU=walt.id, CN=walt.is`, issued by `CN=MDOC ROOT CA`,
         * valid 2025-05-14 to 2075-05-02, P-256 / ecdsa-with-SHA256, X.509 v1.
         */
        val deployedDemoDocumentSigner = CertificateDer(
            Base64.Default.decode(
                "MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTAT" +
                    "BgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0" +
                    "MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwG" +
                    "Vmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYD" +
                    "VQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQU" +
                    "D3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPY" +
                    "k9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8Ox" +
                    "MheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB",
            ),
        )
    }
}
