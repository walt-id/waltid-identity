package id.walt.wallet2.mobile

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProximityValidatedTrustPathTest {
    @Test
    fun validatedScopeStopsAtSelectedAnchorAndChecksRevokedIntermediates() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        try {
            val root = ReaderCertificateProfileFixture.create(runtime, rootHasCrl = false)
            val intermediate = ReaderCertificateProfileFixture.create(runtime, parent = root)
            val leafCrl = intermediate.crl().encodeToBase64Url()
            val goodIntermediateCrl = root.crl().encodeToBase64Url()
            val revokedIntermediateCrl = root.crl(listOf(intermediate.root)).encodeToBase64Url()
            val rootDer = root.root.encodedDer.toByteArray().encodeToBase64Url()
            val intermediateDer = intermediate.root.encodedDer.toByteArray().encodeToBase64Url()
            val evidence = ProximityReaderEvidence(ProximityReaderAuthenticationScope.WholeRequest,
                certificateChainDerBase64Url = listOf(intermediate.leaf.encodedDer.toByteArray().encodeToBase64Url(), intermediateDer))
            suspend fun evaluate(anchor: String, revoked: Boolean, scope: ProximityCrlScope): ProximityReaderTrustDecision {
                val crl = ProximityCrlRevocationEvaluator(listOf(rootDer, intermediateDer), scope,
                    ProximityCrlFetcher { url, _ -> ProximityCrlFetchResult.Available(
                        if (url.endsWith("/intermediate-crl")) {
                            if (revoked) revokedIntermediateCrl else goodIntermediateCrl
                        } else leafCrl
                    ) })
                return ProximityConfiguredReaderTrustEvaluator(ProximityReaderTrustConfiguration(
                    trustAnchors = listOf(ProximityReaderTrustAnchor(anchor)),
                    revocationPolicy = ProximityReaderRevocationPolicy.Check(crl),
                )).evaluate(evidence)
            }
            assertEquals(ProximityReaderRevocationState.Good, evaluate(rootDer, false, ProximityCrlScope.ValidatedPath).revocation)
            assertEquals(ProximityReaderTrustState.Revoked, evaluate(rootDer, true, ProximityCrlScope.ValidatedPath).state)
            // The configured intermediate is outside the required revocation path.
            assertEquals(ProximityReaderRevocationState.Good, evaluate(intermediateDer, true, ProximityCrlScope.ValidatedPath).revocation)
            assertEquals(ProximityReaderRevocationState.Indeterminate,
                evaluate(rootDer, false, ProximityCrlScope.ReaderCertificateAndIssuingAuthorities).revocation)
            val raw = ProximityCrlRevocationEvaluator(listOf(rootDer), ProximityCrlScope.ValidatedPath,
                ProximityCrlFetcher { _, _ -> error("Raw evidence must not trigger path-scope fetching") })
            assertIs<ProximityCertificateRevocationResult.Indeterminate>(raw.evaluate(evidence))
        } finally { runtime.close() }
    }

    @Test
    fun conditionalIacaContactRequiresExactConfiguredDirectIssuer() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        try {
            val fixture = ReaderCertificateProfileFixture.create(runtime)
            val root = fixture.root.encodedDer.toByteArray().encodeToBase64Url()
            suspend fun evaluate(leaf: String, requiredIssuer: String?) =
                ProximityConfiguredReaderTrustEvaluator(ProximityReaderTrustConfiguration(
                    trustAnchors = listOf(ProximityReaderTrustAnchor(root)),
                    requiredIacaIssuerCertificateDerBase64Url = requiredIssuer,
                )).evaluate(ProximityReaderEvidence(ProximityReaderAuthenticationScope.WholeRequest,
                    certificateChainDerBase64Url = listOf(leaf)))
            val ordinary = fixture.leaf.encodedDer.toByteArray().encodeToBase64Url()
            assertEquals(ProximityReaderTrustState.Trusted, evaluate(ordinary, null).state)
            assertEquals(ProximityReaderCertificatePathState.Invalid, evaluate(ordinary, "***").certificatePath)
            assertEquals(ProximityReaderCertificatePathState.Invalid, evaluate(ordinary, root).certificatePath)
            for (contact in listOf("contact-uri", "contact-email")) {
                val leaf = fixture.modified(contact).encodeToBase64Url()
                assertEquals(ProximityReaderTrustState.Trusted, evaluate(leaf, root).state, contact)
                assertEquals(ProximityReaderCertificatePathState.Invalid, evaluate(leaf, ordinary).certificatePath)
            }
            for (contact in listOf("contact-dns", "contact-critical")) {
                assertEquals(ProximityReaderCertificatePathState.Invalid,
                    evaluate(fixture.modified(contact).encodeToBase64Url(), root).certificatePath, contact)
            }
        } finally { runtime.close() }
    }
}
