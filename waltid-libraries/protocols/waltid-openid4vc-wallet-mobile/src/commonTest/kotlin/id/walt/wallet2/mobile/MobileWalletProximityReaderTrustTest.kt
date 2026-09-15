@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ReaderAuthenticationEvidence
import id.walt.mdoc.proximity.ReaderAuthenticationScope
import id.walt.mdoc.proximity.Rical
import id.walt.mdoc.proximity.RicalCertificateInfo
import id.walt.mdoc.proximity.RicalReaderPathResult
import id.walt.mdoc.proximity.X509RicalReaderPathValidator
import id.walt.x509.MdocReaderAuthenticationEkuOid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ProximityReaderTrustTest {
    @Test
    fun `explicit anchor establishes trust without trusting a reader supplied root implicitly`() = runTest {
        withCertificates { certificates ->
            val trusted = evaluator(certificates.root).evaluate(certificates.evidence(includeRoot = false))
            assertEquals(ProximityReaderTrustState.Trusted, trusted.state)
            assertEquals(ProximityReaderCertificatePathState.Valid, trusted.certificatePath)
            assertEquals(ProximityReaderRevocationState.NotChecked, trusted.revocation)
            assertEquals("Example reader", trusted.displayName)

            val otherRoot = certificates.createRoot("Different reader root")
            val unknown = evaluator(otherRoot).evaluate(certificates.evidence(includeRoot = true))
            assertEquals(ProximityReaderTrustState.ValidButUntrusted, unknown.state)
            assertEquals(ProximityReaderCertificatePathState.UnknownAuthority, unknown.certificatePath)
            assertEquals(null, unknown.displayName)
        }
    }

    @Test
    fun `trust policy and evidence remain owned while revocation is suspended`() = runTest {
        withCertificates { certificates ->
            val anchors = mutableListOf(
                ProximityReaderTrustAnchor(certificates.root.base64Url()),
                ProximityReaderTrustAnchor(certificates.createRoot("Other root").base64Url()),
            )
            val entered = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val expected = certificates.evidence(includeRoot = true).certificateChainDerBase64Url
            val evaluator = ProximityConfiguredReaderTrustEvaluator(
                ProximityReaderTrustConfiguration(
                    trustAnchors = anchors,
                    revocationPolicy = ProximityReaderRevocationPolicy.Check(
                        ProximityReaderRevocationEvaluator { evidence ->
                            entered.complete(Unit)
                            resume.await()
                            assertEquals(expected, evidence.certificateChainDerBase64Url)
                            ProximityCertificateRevocationResult.Good
                        },
                    ),
                ),
            )
            val input = expected.toMutableList()
            val pending = async { evaluator.evaluate(certificates.evidence().copy(certificateChainDerBase64Url = input)) }
            entered.await()
            anchors.clear()
            input.clear()
            (evaluator.configuration.trustAnchors as MutableList).clear()
            resume.complete(Unit)
            assertEquals(ProximityReaderTrustState.Trusted, pending.await().state)
            assertEquals(ProximityReaderTrustState.Trusted, evaluator.evaluate(certificates.evidence(includeRoot = true)).state)
        }
    }

    @Test
    fun `reader end entity cannot be configured as its own trust anchor`() = runTest {
        withCertificates { certificates ->
            val decision = evaluator(certificates.reader).evaluate(certificates.evidence())

            assertEquals(ProximityReaderTrustState.ValidButUntrusted, decision.state)
            assertEquals(ProximityReaderCertificatePathState.UnknownAuthority, decision.certificatePath)
        }
    }

    @Test
    fun `invalid reader certificate profile remains distinct from an unknown authority`() = runTest {
        withCertificates(readerExtendedKeyUsage = null) { certificates ->
            val decision = evaluator(certificates.root).evaluate(certificates.evidence())

            assertEquals(ProximityReaderTrustState.ValidButUntrusted, decision.state)
            assertEquals(ProximityReaderCertificatePathState.Invalid, decision.certificatePath)
            assertEquals(ProximityReaderRevocationState.NotChecked, decision.revocation)
        }
    }

    @Test
    fun `expired reader certificate fails profile validation before trust lookup`() = runTest {
        withCertificates { certificates ->
            val decision = evaluator(
                certificates.root,
                now = Clock.System.now() + 31.days,
            ).evaluate(certificates.evidence())

            assertEquals(ProximityReaderTrustState.ValidButUntrusted, decision.state)
            assertEquals(ProximityReaderCertificatePathState.Invalid, decision.certificatePath)
            assertEquals(ProximityReaderRevocationState.NotChecked, decision.revocation)
        }
    }

    @Test
    fun `revoked and indeterminate status fail closed without collapsing path validity`() = runTest {
        withCertificates { certificates ->
            val revoked = evaluator(
                certificates.root,
                ProximityCertificateRevocationResult.Revoked("Revoked by test source"),
            ).evaluate(certificates.evidence())
            assertEquals(ProximityReaderTrustState.Revoked, revoked.state)
            assertEquals(ProximityReaderCertificatePathState.Valid, revoked.certificatePath)
            assertEquals(ProximityReaderRevocationState.Revoked, revoked.revocation)

            val indeterminate = evaluator(
                certificates.root,
                ProximityCertificateRevocationResult.Indeterminate("Status source is offline"),
            ).evaluate(certificates.evidence())
            assertEquals(ProximityReaderTrustState.ValidButUntrusted, indeterminate.state)
            assertEquals(ProximityReaderCertificatePathState.Valid, indeterminate.certificatePath)
            assertEquals(ProximityReaderRevocationState.Indeterminate, indeterminate.revocation)
        }
    }

    @Test
    fun `revocation source exceptions are indeterminate and repeated evaluations are independent`() = runTest {
        withCertificates { certificates ->
            var calls = 0
            val evaluator = ProximityConfiguredReaderTrustEvaluator(
                ProximityReaderTrustConfiguration(
                    trustAnchors = listOf(
                        ProximityReaderTrustAnchor(
                            certificates.root.base64Url(),
                            "Configured reader authority",
                        )
                    ),
                    revocationPolicy = ProximityReaderRevocationPolicy.Check(
                        ProximityReaderRevocationEvaluator {
                            calls += 1
                            if (calls == 1) error("status source offline")
                            ProximityCertificateRevocationResult.Good
                        }
                    ),
                )
            )

            val first = evaluator.evaluate(certificates.evidence())
            val second = evaluator.evaluate(certificates.evidence())

            assertEquals(ProximityReaderRevocationState.Indeterminate, first.revocation)
            assertEquals(ProximityReaderTrustState.ValidButUntrusted, first.state)
            assertEquals(ProximityReaderRevocationState.Good, second.revocation)
            assertEquals(ProximityReaderTrustState.Trusted, second.state)
            assertEquals("Configured reader authority", second.displayName)
            assertEquals(2, calls)
        }
    }

    @Test
    fun `RICAL provider availability and conflicts remain distinct trust facts`() = runTest {
        withCertificates { certificates ->
            suspend fun evaluate(result: ProximityRicalProviderResult) =
                ProximityConfiguredReaderTrustEvaluator(
                    ProximityReaderTrustConfiguration(
                        ricalProviders = listOf(
                            ProximityRicalConfiguration(
                                providerId = "provider",
                                acceptedTypes = setOf("org.iso.18013.5.1.reader_authentication"),
                                providerTrustAnchors = listOf(
                                    ProximityRicalProviderTrustAnchor(certificates.root.base64Url())
                                ),
                                acceptedSignerCertificatePolicyOids = setOf("1.2.3.4"),
                                provider = ProximityRicalProvider { result },
                            )
                        )
                    )
                ).evaluate(certificates.evidence())

            val unavailable = evaluate(ProximityRicalProviderResult.Unavailable("offline"))
            assertEquals(ProximityReaderCertificatePathState.UnknownAuthority, unavailable.certificatePath)
            assertEquals(ProximityRicalState.Unavailable, unavailable.rical)

            val conflict = evaluate(ProximityRicalProviderResult.Conflict("two active lists"))
            assertEquals(ProximityReaderCertificatePathState.UnknownAuthority, conflict.certificatePath)
            assertEquals(ProximityRicalState.Invalid, conflict.rical)
        }
    }

    @Test
    fun `configured RICAL validates signer path policy status and reader authority`() = runTest {
        var signerRevocationChecks = 0
        val evidence = ProximityReaderEvidence(
            scope = ProximityReaderAuthenticationScope.WholeRequest,
            certificateChainDerBase64Url = listOf(RICAL_READER_LEAF),
        )
        val evaluator = ricalEvaluator {
            signerRevocationChecks += 1
            assertEquals("test-provider", it.providerId)
            assertEquals(1, it.certificateChainDerBase64Url.size)
            ProximityCertificateRevocationResult.Good
        }

        val decision = evaluator.evaluate(evidence)

        assertEquals(ProximityReaderTrustState.Trusted, decision.state)
        assertEquals(ProximityReaderCertificatePathState.Valid, decision.certificatePath)
        assertEquals(ProximityReaderRevocationState.NotChecked, decision.revocation)
        assertEquals(ProximityRicalState.Matched, decision.rical)
        assertEquals("Fixture reader authority", decision.displayName)
        assertEquals(1, signerRevocationChecks)

        val revokedSigner = ricalEvaluator {
            ProximityCertificateRevocationResult.Revoked("Signer revoked")
        }.evaluate(evidence)
        assertEquals(ProximityReaderTrustState.ValidButUntrusted, revokedSigner.state)
        assertEquals(ProximityReaderCertificatePathState.UnknownAuthority, revokedSigner.certificatePath)
        assertEquals(ProximityRicalState.Invalid, revokedSigner.rical)
    }

    @Test
    fun `RICAL path validation applies only the bottom-most matching authority`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        try {
            val rootKey = runtime.testKey("rical-reader-root")
            val subCaKey = runtime.testKey("rical-reader-sub-ca")
            val readerKey = runtime.testKey("rical-reader-leaf")
            val root = createRoot(rootKey, "RICAL reader root")
            val subCa = createCa(rootKey, root, subCaKey, "RICAL reader sub CA")
            val reader = createReader(subCaKey, subCa, readerKey, MdocReaderAuthenticationEkuOid)
            val rootInfo = root.ricalInfo(isTrustAnchor = true, name = "Root authority")
            val subCaInfo = subCa.ricalInfo(isTrustAnchor = false, name = "Bottom authority")
            val rical = Rical(
                version = "1.0",
                provider = "test-provider",
                date = Clock.System.now() - 1.days,
                certificateInfos = listOf(rootInfo, subCaInfo),
                type = "org.iso.18013.5.1.reader_authentication",
            )

            val result = X509RicalReaderPathValidator().validate(
                ReaderAuthenticationEvidence(
                    scope = ReaderAuthenticationScope.WholeRequest,
                    certificateChainDer = listOf(reader, subCa).map {
                        ImmutableBytes.of(it.encodedDer.toByteArray())
                    },
                ),
                rical,
            )

            assertEquals(subCaInfo, assertIs<RicalReaderPathResult.Valid>(result).authority)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun `configuration rejects empty and malformed anchors`() {
        assertFailsWith<IllegalArgumentException> {
            ProximityReaderTrustConfiguration()
        }
        assertFailsWith<IllegalArgumentException> {
            ProximityReaderTrustAnchor("not base64 url!")
        }
    }

    @Test
    fun `configuration rejects duplicate anchors`() = runTest {
        withCertificates { certificates ->
            val anchor = ProximityReaderTrustAnchor(certificates.root.base64Url())

            assertFailsWith<IllegalArgumentException> {
                ProximityReaderTrustConfiguration(
                    trustAnchors = listOf(anchor, anchor)
                )
            }
        }
    }

    @Test
    fun `settings codec round trips policy and public trust material`() = runTest {
        withCertificates { certificates ->
            val settings = ProximityReaderTrustSettings(
                readerPolicy = ProximityReaderPolicy.RequireTrusted,
                trustAnchors = listOf(
                    ProximityStoredReaderTrustAnchor(
                        certificates.root.base64Url(),
                        "Local Reader CA",
                    )
                ),
            )

            val encoded = ProximityReaderTrustSettingsCodec.encode(settings)
            val decoded = ProximityReaderTrustSettingsCodec.decode(encoded)

            assertEquals(settings, decoded)
            assertContains(encoded, "require_trusted")
            assertTrue(!encoded.contains("PRIVATE KEY"))
        }
    }

    @Test
    fun `DER and multi PEM imports validate CAs and prepare review without persisting`() = runTest {
        withCertificates { certificates ->
            val second = certificates.createRoot("Second reader root")
            val existing = ProximityReaderTrustSettings(
                readerPolicy = ProximityReaderPolicy.RequireTrusted,
            )

            val der = ProximityReaderTrustSettingsCodec.prepareImport(
                sourceName = "reader-ca.der",
                bytes = certificates.root.encodedDer.toByteArray(),
                existing = existing,
            )
            assertEquals(existing.readerPolicy, der.resultingSettings.readerPolicy)
            assertEquals(1, der.readerAuthorities.size)
            assertEquals("ISO mdoc Reader CA", der.readerAuthorities.single().profile)
            assertTrue(
                Regex("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")
                    .matches(der.readerAuthorities.single().sha256Fingerprint),
            )
            assertEquals(0, existing.trustAnchors.size)

            val pem = ProximityReaderTrustSettingsCodec.prepareImport(
                sourceName = "reader-cas.pem",
                bytes = "${certificates.root.encodedPem}\n${second.encodedPem}".encodeToByteArray(),
                existing = existing,
            )
            assertEquals(2, pem.readerAuthorities.size)
            assertEquals(2, pem.resultingSettings.trustAnchors.size)
        }
    }

    @Test
    fun `import rejects private keys pfx malformed duplicate and non CA material`() = runTest {
        withCertificates { certificates ->
            assertContains(
                assertFailsWith<IllegalArgumentException> {
                    ProximityReaderTrustSettingsCodec.prepareImport(
                        "reader.p12",
                        byteArrayOf(1),
                        ProximityReaderTrustSettings(),
                    )
                }.message.orEmpty(),
                "PKCS#12",
            )
            assertContains(
                assertFailsWith<IllegalArgumentException> {
                    ProximityReaderTrustSettingsCodec.prepareImport(
                        "secret.pem",
                        "-----BEGIN PRIVATE KEY-----\nAA==\n-----END PRIVATE KEY-----".encodeToByteArray(),
                        ProximityReaderTrustSettings(),
                    )
                }.message.orEmpty(),
                "Private keys",
            )
            assertFailsWith<IllegalArgumentException> {
                ProximityReaderTrustSettingsCodec.prepareImport(
                    "broken.der",
                    byteArrayOf(1, 2, 3),
                    ProximityReaderTrustSettings(),
                )
            }
            assertContains(
                assertFailsWith<IllegalArgumentException> {
                    ProximityReaderTrustSettingsCodec.prepareImport(
                        "reader.der",
                        certificates.reader.encodedDer.toByteArray(),
                        ProximityReaderTrustSettings(),
                    )
                }.message.orEmpty(),
                "not a valid current CA",
            )
            val existing = ProximityReaderTrustSettings(
                trustAnchors = listOf(
                    ProximityStoredReaderTrustAnchor(
                        certificates.root.base64Url(),
                        "Existing",
                    )
                )
            )
            assertContains(
                assertFailsWith<IllegalArgumentException> {
                    ProximityReaderTrustSettingsCodec.prepareImport(
                        "reader.der",
                        certificates.root.encodedDer.toByteArray(),
                        existing,
                    )
                }.message.orEmpty(),
                "Duplicate",
            )
        }
    }

    @Test
    fun `strict bundle validates signed RICAL and rejects unknown or expired data`() = runTest {
        val bundle = ricalBundleJson()
        val preview = ProximityReaderTrustSettingsCodec.prepareImport(
            sourceName = "qualification.walt-reader-trust.json",
            bytes = bundle.encodeToByteArray(),
            existing = ProximityReaderTrustSettings(
                readerPolicy = ProximityReaderPolicy.RequireTrusted,
            ),
            now = Instant.parse("2026-09-02T00:00:00Z"),
        )

        assertEquals(ProximityReaderTrustImportKind.TrustBundle, preview.kind)
        assertEquals(1, preview.ricalProviders.size)
        assertTrue(preview.ricalProviders.single().establishesReaderTrust)
        assertContains(preview.policyEffect, "Only readers trusted")

        assertFailsWith<IllegalArgumentException> {
            ProximityReaderTrustSettingsCodec.prepareImport(
                "unknown.json",
                bundle.replaceFirst("\"version\": 1,", "\"version\": 1, \"unknown\": true,")
                    .encodeToByteArray(),
                ProximityReaderTrustSettings(),
            )
        }
        assertContains(
            assertFailsWith<IllegalArgumentException> {
                ProximityReaderTrustSettingsCodec.prepareImport(
                    "expired.json",
                    bundle.encodeToByteArray(),
                    ProximityReaderTrustSettings(),
                    now = Instant.parse("2028-01-01T00:00:00Z"),
                )
            }.message.orEmpty(),
            "current CA",
        )
    }

    @Test
    fun `import rejects oversized truncated and unsupported version data`() = runTest {
        assertContains(
            assertFailsWith<IllegalArgumentException> {
                ProximityReaderTrustSettingsCodec.prepareImport(
                    "oversized.der",
                    ByteArray(ProximityReaderTrustSettingsCodec.MaximumImportBytes + 1),
                    ProximityReaderTrustSettings(),
                )
            }.message.orEmpty(),
            "exceeds 1 MiB",
        )
        assertFailsWith<IllegalArgumentException> {
            ProximityReaderTrustSettingsCodec.prepareImport(
                "truncated.pem",
                "-----BEGIN CERTIFICATE-----\nAA".encodeToByteArray(),
                ProximityReaderTrustSettings(),
            )
        }
        assertContains(
            assertFailsWith<IllegalArgumentException> {
                ProximityReaderTrustSettingsCodec.prepareImport(
                    "unsupported.json",
                    """{"version":2,"type":"org.waltid.wallet.reader-trust","readerAuthorities":[],"ricalProviders":[]}"""
                        .encodeToByteArray(),
                    ProximityReaderTrustSettings(),
                )
            }.message.orEmpty(),
            "version",
        )
    }

    @Test
    fun `each proximity session receives an immutable settings snapshot`() = runTest {
        withCertificates { certificates ->
            val trusted = ProximityReaderTrustSettings(
                readerPolicy = ProximityReaderPolicy.RequireTrusted,
                trustAnchors = listOf(
                    ProximityStoredReaderTrustAnchor(
                        certificates.root.base64Url(),
                        "Snapshot authority",
                    )
                ),
            ).applyTo(ProximityConfiguration())
            val reset = ProximityReaderTrustSettings()
                .applyTo(ProximityConfiguration())

            assertEquals(ProximityReaderPolicy.RequireTrusted, trusted.readerPolicy)
            assertEquals(
                ProximityReaderTrustState.Trusted,
                trusted.readerTrustEvaluator.evaluate(certificates.evidence()).state,
            )
            assertEquals(
                ProximityReaderTrustState.ValidButUntrusted,
                reset.readerTrustEvaluator.evaluate(certificates.evidence()).state,
            )
            assertNotSame(trusted.readerTrustEvaluator, reset.readerTrustEvaluator)
        }
    }

    private fun ricalBundleJson(): String = """
        {
          "version": 1,
          "type": "org.waltid.wallet.reader-trust",
          "ricalProviders": [
            {
              "providerId": "test-provider",
              "acceptedTypes": ["org.iso.18013.5.1.reader_authentication"],
              "providerTrustAnchorsDerBase64Url": ["$RICAL_PROVIDER_ROOT"],
              "acceptedSignerCertificatePolicyOids": ["1.2.3.4"],
              "establishReaderTrust": true,
              "signedRicalBase64Url": "$SIGNED_RICAL"
            }
          ]
        }
    """.trimIndent()

    private fun evaluator(
        root: X509Certificate,
        revocation: ProximityCertificateRevocationResult? = null,
        now: Instant = Clock.System.now(),
    ): ProximityConfiguredReaderTrustEvaluator = ProximityConfiguredReaderTrustEvaluator(
        ProximityReaderTrustConfiguration(
            trustAnchors = listOf(ProximityReaderTrustAnchor(root.base64Url())),
            revocationPolicy = revocation?.let { result ->
                ProximityReaderRevocationPolicy.Check(
                    ProximityReaderRevocationEvaluator { result }
                )
            } ?: ProximityReaderRevocationPolicy.NotChecked,
        ),
        now = { now },
    )

    private fun ricalEvaluator(
        signerRevocationEvaluator: ProximityRicalSignerRevocationEvaluator,
    ): ProximityConfiguredReaderTrustEvaluator =
        ProximityConfiguredReaderTrustEvaluator(
            configuration = ProximityReaderTrustConfiguration(
                ricalProviders = listOf(
                    ProximityRicalConfiguration(
                        providerId = "test-provider",
                        acceptedTypes = setOf("org.iso.18013.5.1.reader_authentication"),
                        providerTrustAnchors = listOf(
                            ProximityRicalProviderTrustAnchor(RICAL_PROVIDER_ROOT)
                        ),
                        acceptedSignerCertificatePolicyOids = setOf("1.2.3.4"),
                        signerRevocationPolicy = ProximityRicalSignerRevocationPolicy.Check(
                            signerRevocationEvaluator
                        ),
                        establishReaderTrust = true,
                        provider = ProximityRicalProvider {
                            ProximityRicalProviderResult.Available(SIGNED_RICAL)
                        },
                    )
                )
            ),
            now = { Instant.parse("2026-09-02T00:00:00Z") },
        )

    private suspend fun <T> withCertificates(
        readerExtendedKeyUsage: String? = MdocReaderAuthenticationEkuOid,
        block: suspend (Certificates) -> T,
    ): T {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        return try {
            val rootKey = runtime.testKey("reader-root")
            val readerKey = runtime.testKey("reader-leaf")
            val root = createRoot(rootKey, "Example reader root")
            val reader = createReader(rootKey, root, readerKey, readerExtendedKeyUsage)
            block(Certificates(runtime, root, reader))
        } finally {
            runtime.close()
        }
    }

    private suspend fun CryptoRuntime.testKey(id: String): Key = generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private suspend fun createRoot(key: Key, commonName: String): X509Certificate =
        X509CertificateUtil.createSelfSignedCertificate(key, certificateAlgorithm) {
            subjectDn = "CN=$commonName"
            extensionKeyUsage {
                critical = true
                addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
            }
        }

    private suspend fun createReader(
        issuerKey: Key,
        issuer: X509Certificate,
        readerKey: Key,
        extendedKeyUsage: String?,
    ): X509Certificate = X509CertificateUtil.createCertificate(
        issuerKey = issuerKey,
        issuerCert = issuer,
        signatureAlgorithm = certificateAlgorithm,
    ) {
        subjectDn = "CN=Example reader"
        subjectPublicKey(readerKey)
        extensionSubjectKeyIdentifier()
        extensionKeyUsage {
            critical = true
            addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature)
        }
        extendedKeyUsage?.let { oid ->
            extensionExtendedKeyUsage {
                critical = true
                addKeyUsage(oid)
            }
        }
        extensionCrlDistributionPoints {
            addUriDistributionPoint("https://reader.example/crl")
        }
    }

    private suspend fun createCa(
        issuerKey: Key,
        issuer: X509Certificate,
        subjectKey: Key,
        commonName: String,
    ): X509Certificate = X509CertificateUtil.createCertificate(
        issuerKey = issuerKey,
        issuerCert = issuer,
        signatureAlgorithm = certificateAlgorithm,
    ) {
        subjectDn = "CN=$commonName"
        subjectPublicKey(subjectKey)
        extensionSubjectKeyIdentifier()
        extensionBasicConstraints { cA = true }
        extensionKeyUsage {
            critical = true
            addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
        }
    }

    private fun X509Certificate.ricalInfo(
        isTrustAnchor: Boolean,
        name: String,
    ): RicalCertificateInfo = RicalCertificateInfo(
        certificateDer = ImmutableBytes.of(encodedDer.toByteArray()),
        serialNumber = ImmutableBytes.of(data.serialNumberRaw.toByteArray()),
        subjectKeyIdentifier = ImmutableBytes.of(
            requireNotNull(data.extensionSubjectKeyIdentifier).keyIdentifier.toByteArray()
        ),
        isTrustAnchor = isTrustAnchor,
        authorityKeyIdentifier = data.extensionAuthorityKeyIdentifier?.keyIdentifier?.toByteArray()?.let {
            ImmutableBytes.of(it)
        },
        name = name,
    )

    private inner class Certificates(
        private val runtime: CryptoRuntime,
        val root: X509Certificate,
        val reader: X509Certificate,
    ) {
        fun evidence(includeRoot: Boolean = false): ProximityReaderEvidence =
            ProximityReaderEvidence(
                scope = ProximityReaderAuthenticationScope.WholeRequest,
                certificateChainDerBase64Url = buildList {
                    add(reader.base64Url())
                    if (includeRoot) add(root.base64Url())
                },
            )

        suspend fun createRoot(commonName: String): X509Certificate =
            this@ProximityReaderTrustTest.createRoot(
                runtime.testKey(commonName.replace(' ', '-')),
                commonName,
            )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun X509Certificate.base64Url(): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(encodedDer.toByteArray())

    private companion object {
        val certificateAlgorithm = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )

        // Public-only test material. The corresponding private keys are intentionally not retained.
        const val RICAL_PROVIDER_ROOT = "MIIBoDCCAUWgAwIBAgIIQAAAAAAAAAEwCgYIKoZIzj0EAwIwIzEhMB8GA1UEAwwYUklDQUwgUHJvdmlkZXIgVGVzdCBSb290MB4XDTI2MDgzMDIwNTgyMloXDTI3MDgzMDIwNTgyMlowIzEhMB8GA1UEAwwYUklDQUwgUHJvdmlkZXIgVGVzdCBSb290MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEdlAtVZ6AIUdT6Dz40ocIC1beZ4jBMkBCbIYT9aYANmpUIcfrbF7p0hxAMU_e3aFcOc0gE-3ctXDS_XYJ9pVw06NjMGEwDwYDVR0TAQH_BAUwAwEB_zAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFOu5ldR964dGMxFoyzr-Wfg3SLBxMB8GA1UdIwQYMBaAFOu5ldR964dGMxFoyzr-Wfg3SLBxMAoGCCqGSM49BAMCA0kAMEYCIQDrTcqqxUk0W-nVpNdQXtIiuYkPO3fewH-jasVtnXEGuQIhAKc2LKRf3nZ93kCzVSErxvSiacBApOVD7o6F5yhBqUXC"
        const val RICAL_READER_LEAF = "MIIBwjCCAWigAwIBAgIIQAAAAAAAAAQwCgYIKoZIzj0EAwIwGzEZMBcGA1UEAwwQUmVhZGVyIFRlc3QgUm9vdDAeFw0yNjA4MzAyMDU4MjJaFw0yNjA5MjkyMDU4MjJaMBkxFzAVBgNVBAMMDkZpeHR1cmUgUmVhZGVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0vfmnCwPggx1aO8shICyUA_M1mYt7CXaWNZyY4_uLQna7LISphQ7cjy2xoLCN9oT4Bqhewunrw7RKvH6_NVStKOBlzCBlDAdBgNVHQ4EFgQU-OVW6w6H38FtAa_Q1fKlUUn0qp4wHwYDVR0jBBgwFoAUhdjG_yjle9d7MBgoC9S57eK3_x8wDgYDVR0PAQH_BAQDAgeAMBUGA1UdJQEB_wQLMAkGByiBjF0FAQYwKwYDVR0fBCQwIjAgoB6gHIYaaHR0cHM6Ly9yZWFkZXIuZXhhbXBsZS9jcmwwCgYIKoZIzj0EAwIDSAAwRQIhAPQgGNKygRTaykDwA1SMy_yrm8y3xhIuoZnylecMEbtOAiAa_3jyEp3ZYuaXkDdegFYUySpSF_4JvCXEiVdvecyzDA"
        const val SIGNED_RICAL = "hFkCAqIBJhghgVkB-TCCAfUwggGaoAMCAQICCEAAAAAAAAACMAoGCCqGSM49BAMCMCMxITAfBgNVBAMMGFJJQ0FMIFByb3ZpZGVyIFRlc3QgUm9vdDAeFw0yNjA4MzAyMDU4MjJaFw0yNjA5MjkyMDU4MjJaMEkxCzAJBgNVBAYTAkFUMR4wHAYDVQQKDBV3YWx0LmlkIHRlc3QgZml4dHVyZXMxGjAYBgNVBAMMEVJJQ0FMIFRlc3QgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEpzO3bwxEx36dnpKkcMefEPyAldJM4kLPoP2eUTFi89Lr69Nfwh6ho_ANzdgcbRFMCBsp4S9UbSMB0hUeOozEDaOBkTCBjjAdBgNVHQ4EFgQUO_-jL0aDjkbcm6CgIHXWrUsbngUwHwYDVR0jBBgwFoAU67mV1H3rh0YzEWjLOv5Z-DdIsHEwDgYDVR0PAQH_BAQDAgZAMCoGA1UdHwQjMCEwH6AdoBuGGWh0dHBzOi8vcmljYWwuZXhhbXBsZS9jcmwwEAYDVR0gBAkwBzAFBgMqAwQwCgYIKoZIzj0EAwIDSQAwRgIhALZ_msangnrrXhHQdoVeHngvNkDTuUQF4twPPA5i-2XWAiEA1qWFVUwCpqpiQD7N5EvpQ4DTxi02m36JHMNdJvrxK-OgWQKepmd2ZXJzaW9uYzEuMGhwcm92aWRlcm10ZXN0LXByb3ZpZGVyZGRhdGXAdDIwMjYtMDktMDFUMDA6MDA6MDBaaG5vdEFmdGVywHQyMDI3LTAxLTAxVDAwOjAwOjAwWnBjZXJ0aWZpY2F0ZUluZm9zgaVrY2VydGlmaWNhdGVZAZIwggGOMIIBNaADAgECAghAAAAAAAAAAzAKBggqhkjOPQQDAjAbMRkwFwYDVQQDDBBSZWFkZXIgVGVzdCBSb290MB4XDTI2MDgzMDIwNTgyMloXDTI3MDgzMDIwNTgyMlowGzEZMBcGA1UEAwwQUmVhZGVyIFRlc3QgUm9vdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABNYS1aGguJw1UW2bz_u4vhWK8NodDaUpB2QWq-aO9lLKtY9wrW3684IP-gcSALoEMNlFMlXLeOSS8rXHVIm1WTKjYzBhMA8GA1UdEwEB_wQFMAMBAf8wDgYDVR0PAQH_BAQDAgEGMB0GA1UdDgQWBBSF2Mb_KOV713swGCgL1Lnt4rf_HzAfBgNVHSMEGDAWgBSF2Mb_KOV713swGCgL1Lnt4rf_HzAKBggqhkjOPQQDAgNHADBEAiAPPd02BXTI7cCZxMxA8t9FZn4axqr2vI0v4Cg6mQswzwIgOm7mE-Y634y8cEkNSwCXy1DL6Od4D6HSmV5nswEqIYdsc2VyaWFsTnVtYmVywkhAAAAAAAAAA2Nza2lUAQIDBAUGBwgJCgsMDQ4PEBESExRtaXNUcnVzdEFuY2hvcvVkbmFtZXgYRml4dHVyZSByZWFkZXIgYXV0aG9yaXR5ZHR5cGV4J29yZy5pc28uMTgwMTMuNS4xLnJlYWRlcl9hdXRoZW50aWNhdGlvblhA5kQFmKh6ysjSmnvTqcE5vCffjsF_BlaAYXFMH8QIuoCZqrd2C0k0v7QsFjJpCS8T9zJpt92-lgOre5fkOVA2rg"
    }
}
