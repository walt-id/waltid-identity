package id.walt.wallet2.mobile

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun knownIacaDirectIssuerRequiresContactExtension() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        try {
            val fixture = ReaderCertificateProfileFixture.create(runtime)
            val root = fixture.root.encodedDer.toByteArray().encodeToBase64Url()
            val evaluator = ProximityConfiguredReaderTrustEvaluator(ProximityReaderTrustConfiguration(
                trustAnchors = listOf(ProximityReaderTrustAnchor(root)),
                knownIacaIssuers = listOf(ProximityKnownIacaIssuer(root)),
            ))
            suspend fun evaluate(leaf: ByteArray) = evaluator.evaluate(ProximityReaderEvidence(
                ProximityReaderAuthenticationScope.WholeRequest,
                certificateChainDerBase64Url = listOf(leaf.encodeToBase64Url()),
            ))
            assertEquals(ProximityReaderCertificatePathState.Invalid,
                evaluate(fixture.leaf.encodedDer.toByteArray()).certificatePath)
            for (contact in listOf("contact-uri", "contact-email")) {
                assertEquals(ProximityReaderTrustState.Trusted, evaluate(fixture.modified(contact)).state, contact)
            }
            for (contact in listOf("contact-dns", "contact-critical")) {
                assertEquals(ProximityReaderCertificatePathState.Invalid,
                    evaluate(fixture.modified(contact)).certificatePath, contact)
            }
        } finally { runtime.close() }
    }

    @Test
    fun iacaKnowledgeNeitherPinsIssuersNorAddsTrust() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        try {
            val known = ReaderCertificateProfileFixture.create(runtime)
            val other = ReaderCertificateProfileFixture.create(runtime)
            val knownRoot = known.root.encodedDer.toByteArray().encodeToBase64Url()
            val otherRoot = other.root.encodedDer.toByteArray().encodeToBase64Url()
            suspend fun evaluate(anchor: String, leaf: ByteArray, issuers: List<ProximityKnownIacaIssuer>) =
                ProximityConfiguredReaderTrustEvaluator(ProximityReaderTrustConfiguration(
                    trustAnchors = listOf(ProximityReaderTrustAnchor(anchor)),
                    knownIacaIssuers = issuers,
                )).evaluate(ProximityReaderEvidence(ProximityReaderAuthenticationScope.WholeRequest,
                    certificateChainDerBase64Url = listOf(leaf.encodeToBase64Url())))
            val issuers = listOf(ProximityKnownIacaIssuer(knownRoot))
            // No external role knowledge: an otherwise valid reader does not need contact information.
            assertEquals(ProximityReaderTrustState.Trusted,
                evaluate(knownRoot, known.leaf.encodedDer.toByteArray(), emptyList()).state)
            // Another trusted issuer remains eligible even without a contact extension.
            assertEquals(ProximityReaderTrustState.Trusted,
                evaluate(otherRoot, other.leaf.encodedDer.toByteArray(), issuers).state)
            // Role knowledge plus a valid contact extension cannot establish trust.
            val untrusted = evaluate(otherRoot, known.modified("contact-uri"), issuers)
            assertEquals(ProximityReaderTrustState.ValidButUntrusted, untrusted.state)
        } finally { runtime.close() }
    }

    @Test
    fun iacaContactRequirementUsesDirectIssuerNotAncestor() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        try {
            val root = ReaderCertificateProfileFixture.create(runtime)
            val intermediate = ReaderCertificateProfileFixture.create(runtime, parent = root)
            val rootDer = root.root.encodedDer.toByteArray().encodeToBase64Url()
            val intermediateDer = intermediate.root.encodedDer.toByteArray().encodeToBase64Url()
            val evidence = ProximityReaderEvidence(ProximityReaderAuthenticationScope.WholeRequest,
                certificateChainDerBase64Url = listOf(
                    intermediate.leaf.encodedDer.toByteArray().encodeToBase64Url(), intermediateDer))
            suspend fun evaluate(issuers: List<ProximityKnownIacaIssuer>) =
                ProximityConfiguredReaderTrustEvaluator(ProximityReaderTrustConfiguration(
                    trustAnchors = listOf(ProximityReaderTrustAnchor(rootDer)),
                    knownIacaIssuers = issuers,
                )).evaluate(evidence)
            assertEquals(ProximityReaderTrustState.Trusted,
                evaluate(listOf(ProximityKnownIacaIssuer(rootDer))).state)
            assertEquals(ProximityReaderCertificatePathState.Invalid,
                evaluate(listOf(ProximityKnownIacaIssuer(rootDer), ProximityKnownIacaIssuer(intermediateDer))).certificatePath)
        } finally { runtime.close() }
    }

    @Test
    fun evaluatorSnapshotsKnownIacaIssuers() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        try {
            val fixture = ReaderCertificateProfileFixture.create(runtime)
            val root = fixture.root.encodedDer.toByteArray().encodeToBase64Url()
            val issuers = mutableListOf(ProximityKnownIacaIssuer(root))
            val evaluator = ProximityConfiguredReaderTrustEvaluator(ProximityReaderTrustConfiguration(
                trustAnchors = listOf(ProximityReaderTrustAnchor(root)), knownIacaIssuers = issuers,
            ))
            issuers.clear()
            assertEquals(1, evaluator.configuration.knownIacaIssuers.size)
            assertEquals(ProximityReaderCertificatePathState.Invalid, evaluator.evaluate(ProximityReaderEvidence(
                ProximityReaderAuthenticationScope.WholeRequest,
                certificateChainDerBase64Url = listOf(fixture.leaf.encodedDer.toByteArray().encodeToBase64Url()),
            )).certificatePath)
        } finally { runtime.close() }
    }

    @Test
    fun knownIacaIssuerRejectsInvalidEncodingAtConstruction() {
        for (invalid in listOf("", "***", byteArrayOf(1, 2, 3).encodeToBase64Url())) {
            assertFailsWith<IllegalArgumentException> { ProximityKnownIacaIssuer(invalid) }
        }
    }
}
