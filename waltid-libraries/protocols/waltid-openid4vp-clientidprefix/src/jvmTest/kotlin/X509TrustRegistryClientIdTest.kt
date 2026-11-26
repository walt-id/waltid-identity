import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.ClientIdX509TrustRegistry
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.prefixes.VerifierAttestation
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.openid4vp.clientidprefix.prefixes.X509SanDns
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.trust.decision.TrustDecisionRequest
import id.walt.trust.decision.TrustPurpose
import id.walt.trust.decision.TrustSubject
import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.SourceAssurance
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntity
import id.walt.trust.model.TrustedEntityType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * WAL-1485: `x509_hash` / `x509_san_dns` decide trust through a [TrustDecisionEvaluator] when
 * [ClientIdTrustConfiguration.x509TrustRegistry] is set, under `RegistryFresh`.
 */
class X509TrustRegistryClientIdTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
    private val dnsName = "verifier.example.com"
    private val metadataJson = """{ "vp_formats_supported": {} }"""
    private val sanDnsClientId = X509SanDns(dnsName, "x509_san_dns:$dnsName")

    private class Presented(val requestObject: String, val chain: List<X509Certificate>)

    /** Answers with [decision] and records every request it is asked. */
    private class RecordingEvaluator(private val decision: TrustDecision) : TrustDecisionEvaluator {
        val requests = mutableListOf<TrustDecisionRequest>()
        override suspend fun evaluate(request: TrustDecisionRequest): TrustDecision {
            requests += request
            return decision
        }
    }

    private fun decision(
        code: TrustDecisionCode = TrustDecisionCode.TRUSTED,
        freshness: FreshnessState = FreshnessState.FRESH,
        authenticity: AuthenticityState = AuthenticityState.AUTHENTICATED,
        entityType: TrustedEntityType = TrustedEntityType.RELYING_PARTY_PROVIDER,
    ) = TrustDecision(
        decision = code,
        sourceFreshness = freshness,
        sourceAssurance = SourceAssurance(authenticityState = authenticity, accepted = true),
        matchedEntity = TrustedEntity("rp-1", "rp-list", entityType, "Example Verifier"),
    )

    private fun registryTrust(evaluator: TrustDecisionEvaluator, requireAuthenticatedSource: Boolean = false) =
        ClientIdTrustConfiguration(
            x509TrustRegistry = ClientIdX509TrustRegistry(evaluator, requireAuthenticatedSource = requireAuthenticatedSource)
        )

    private suspend fun newRoot(): Pair<Key, X509Certificate> {
        val key = SanDnsTests.genKey("registry-root")
        return key to X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "cn=Registry Root, o=Walt.id"
            extensionBasicConstraints { cA = true }
            extensionKeyUsage {
                addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
            }
        }
    }

    /** A request object signed by a leaf issued by a fresh root, `x5c` = [x5c] of leaf and root (leaf + root by default). */
    private suspend fun presented(
        extendedKeyUsage: String = "1.3.6.1.5.5.7.3.2",
        keyUsage: KeyUsageExtension.KeyUsage = KeyUsageExtension.KeyUsage.digitalSignature,
        leafValidity: X509Certificate.Validity? = null,
        signWithOtherKey: Boolean = false,
        x5c: (leaf: X509Certificate, root: X509Certificate) -> List<X509Certificate> = { leaf, root -> listOf(leaf, root) },
    ): Presented {
        val (rootKey, root) = newRoot()
        val leafKey = SanDnsTests.genKey("registry-leaf")
        val leaf = X509CertificateUtil.createCertificate(rootKey, root, sigAlg) {
            subjectDn = "cn=$dnsName"
            subjectPublicKey(leafKey)
            leafValidity?.let { validity = it }
            extensionKeyUsage { addKeyUsage(keyUsage) }
            extensionExtendedKeyUsage { addKeyUsage(extendedKeyUsage) }
            extensionSan { addDnsName(dnsName) }
        }
        val chain = x5c(leaf, root)
        val requestObject = CompactJws.sign(
            "{}".encodeToByteArray(),
            if (signWithOtherKey) SanDnsTests.genKey("registry-forger") else leafKey,
            JwsAlgorithm.ES256,
            buildJsonObject {
                put("x5c", JsonArray(chain.map { JsonPrimitive(Base64.encode(it.encodedDer.toByteArray())) }))
            },
        )
        return Presented(requestObject, chain)
    }

    private fun context(clientId: String, presented: Presented, redirectUri: String? = null) = RequestContext(
        clientId = clientId,
        clientMetadataString = metadataJson,
        requestObjectJws = presented.requestObject,
        redirectUri = redirectUri,
        responseUri = if (redirectUri == null) "https://responses.example.org/response" else null,
    )

    private suspend fun authenticateSanDns(presented: Presented, trust: ClientIdTrustConfiguration, redirectUri: String? = null) =
        sanDnsClientId.authenticateX509SanDns(sanDnsClientId, context(sanDnsClientId.rawValue, presented, redirectUri), trust)

    private fun hashClientIdOf(certificate: X509Certificate): X509Hash {
        val raw = X509Hash.clientIdForCertificate(certificate.encodedDer.toByteArray())
        return X509Hash(raw.removePrefix("x509_hash:"), raw)
    }

    private suspend fun authenticateHash(presented: Presented, trust: ClientIdTrustConfiguration, clientId: X509Hash = hashClientIdOf(presented.chain.first())) =
        clientId.authenticateX509Hash(clientId, context(clientId.rawValue, presented), trust)

    @Test
    fun `x509_san_dns and x509_hash authenticate a chain the registry trusts, without local anchors`() = runTest {
        val presented = presented()
        val expectedChain = presented.chain.map { Base64.encode(it.encodedDer.toByteArray()) }

        for (prefix in listOf("x509_san_dns", "x509_hash")) {
            val evaluator = RecordingEvaluator(decision())
            val trust = registryTrust(evaluator)
            val result = if (prefix == "x509_san_dns") authenticateSanDns(presented, trust) else authenticateHash(presented, trust)

            assertIs<ClientValidationResult.Success>(result, "$prefix: $result")
            val request = evaluator.requests.single()
            assertEquals(TrustPurpose.WALLET_PRESENTATION_AUTH_REQUEST, request.purpose, prefix)
            assertEquals(TrustSubject.CertificateChain(expectedChain), request.subject, prefix)
            assertEquals(ClientIdX509TrustRegistry.RELYING_PARTY_ENTITY_TYPES, request.expectedEntityTypes, prefix)
        }
    }

    @Test
    fun `a registry decision short of RegistryFresh is a denial`() = runTest {
        val presented = presented()
        val denials = mapOf(
            "not trusted" to registryTrust(RecordingEvaluator(decision(code = TrustDecisionCode.NOT_TRUSTED))),
            "stale source" to registryTrust(RecordingEvaluator(decision(freshness = FreshnessState.STALE))),
            "other entity type" to registryTrust(RecordingEvaluator(decision(entityType = TrustedEntityType.PID_PROVIDER))),
            "unauthenticated source" to registryTrust(
                RecordingEvaluator(decision(authenticity = AuthenticityState.UNVERIFIED)),
                requireAuthenticatedSource = true,
            ),
        )

        for ((case, trust) in denials) {
            for (failure in listOf(authenticateSanDns(presented, trust), authenticateHash(presented, trust))) {
                assertEquals(ClientIdError.X509TrustAnchorMismatch, assertIs<ClientValidationResult.Failure>(failure, case).error, case)
            }
        }
    }

    @Test
    fun `an unknown decision is reported as an unavailable trust source`() = runTest {
        val presented = presented()
        val trust = registryTrust(RecordingEvaluator(TrustDecision(TrustDecisionCode.UNKNOWN, warnings = listOf("unreachable"))))

        for (failure in listOf(authenticateSanDns(presented, trust), authenticateHash(presented, trust))) {
            assertEquals(ClientIdError.X509TrustSourceUnavailable, assertIs<ClientValidationResult.Failure>(failure).error)
        }
    }

    @Test
    fun `an evaluator failure propagates instead of becoming a trust decision`() = runTest {
        val presented = presented()
        val trust = registryTrust(TrustDecisionEvaluator { error("registry store is down") })

        assertFailsWith<IllegalStateException> { authenticateSanDns(presented, trust) }
        assertFailsWith<IllegalStateException> { authenticateHash(presented, trust) }
    }

    @Test
    fun `the registry is not asked about a forged, unbound or misprofiled request`() = runTest {
        val evaluator = RecordingEvaluator(decision())
        val trust = registryTrust(evaluator)
        val valid = presented()

        val failures = mapOf(
            "san_dns forged signature" to authenticateSanDns(presented(signWithOtherKey = true), trust),
            "hash forged signature" to authenticateHash(presented(signWithOtherKey = true), trust),
            "san_dns redirect host" to authenticateSanDns(valid, trust, redirectUri = "https://elsewhere.example.org/cb"),
            "hash of another certificate" to authenticateHash(valid, trust, clientId = hashClientIdOf(valid.chain.last())),
            "san_dns leaf without a verifier EKU" to authenticateSanDns(presented(extendedKeyUsage = "1.3.6.1.5.5.7.3.1"), trust),
            "hash leaf without a verifier EKU" to authenticateHash(presented(extendedKeyUsage = "1.3.6.1.5.5.7.3.1"), trust),
            "san_dns leaf without digitalSignature" to authenticateSanDns(presented(keyUsage = KeyUsageExtension.KeyUsage.keyEncipherment), trust),
            "hash leaf without digitalSignature" to authenticateHash(presented(keyUsage = KeyUsageExtension.KeyUsage.keyEncipherment), trust),
        )

        assertEquals(ClientIdError.InvalidSignature, assertIs<ClientValidationResult.Failure>(failures.getValue("san_dns forged signature")).error)
        assertEquals(ClientIdError.InvalidSignature, assertIs<ClientValidationResult.Failure>(failures.getValue("hash forged signature")).error)
        assertIs<ClientIdError.RedirectUriHostMismatch>(assertIs<ClientValidationResult.Failure>(failures.getValue("san_dns redirect host")).error)
        assertEquals(ClientIdError.X509HashMismatch, assertIs<ClientValidationResult.Failure>(failures.getValue("hash of another certificate")).error)
        mapOf(
            "san_dns leaf without a verifier EKU" to "Extended Key Usage",
            "hash leaf without a verifier EKU" to "Extended Key Usage",
            "san_dns leaf without digitalSignature" to "digitalSignature",
            "hash leaf without digitalSignature" to "digitalSignature",
        ).forEach { (case, expected) ->
            val profile = assertIs<ClientIdError.AttestationError>(assertIs<ClientValidationResult.Failure>(failures.getValue(case), case).error, case)
            assertTrue(expected in profile.reason, "$case: ${profile.reason}")
        }
        assertTrue(evaluator.requests.isEmpty(), "registry was asked ${evaluator.requests.size} times")
    }

    @Test
    fun `the registry is not asked about x5c that is not one valid leaf-first chain`() = runTest {
        val evaluator = RecordingEvaluator(decision())
        val trust = registryTrust(evaluator)
        val (_, unrelatedRoot) = newRoot()
        val now = Clock.System.now()
        val cases = mapOf(
            "unrelated certificate appended" to (presented(x5c = { leaf, root -> listOf(leaf, root, unrelatedRoot) }) to "leaf-first"),
            "root first" to (presented(x5c = { leaf, root -> listOf(root, leaf) }) to "leaf-first"),
            "leaf repeated" to (presented(x5c = { leaf, root -> listOf(leaf, leaf, root) }) to "leaf-first"),
            "expired leaf" to (presented(leafValidity = X509Certificate.Validity(now - 2.days, now - 1.days)) to "validity"),
            "leaf not yet valid" to (presented(leafValidity = X509Certificate.Validity(now + 1.days, now + 2.days)) to "validity"),
        )

        for ((case, expectation) in cases) {
            val (presented, expected) = expectation
            for (failure in listOf(authenticateSanDns(presented, trust), authenticateHash(presented, trust))) {
                val error = assertIs<ClientIdError.AttestationError>(assertIs<ClientValidationResult.Failure>(failure, case).error, case)
                assertTrue(expected in error.reason, "$case: ${error.reason}")
            }
        }
        assertTrue(evaluator.requests.isEmpty(), "registry was asked ${evaluator.requests.size} times")
    }

    @Test
    fun `a leaf-only x5c reaches the registry`() = runTest {
        val evaluator = RecordingEvaluator(decision())
        val presented = presented(x5c = { leaf, _ -> listOf(leaf) })

        assertIs<ClientValidationResult.Success>(authenticateSanDns(presented, registryTrust(evaluator)))
        assertEquals(1, evaluator.requests.size)
    }

    @Test
    fun `x509 prefixes without anchors or registry still fail closed`() = runTest {
        val presented = presented()
        val trust = ClientIdTrustConfiguration()

        for (failure in listOf(authenticateSanDns(presented, trust), authenticateHash(presented, trust))) {
            assertEquals(ClientIdError.MissingX509TrustAnchors, assertIs<ClientValidationResult.Failure>(failure).error)
        }
    }

    @Test
    fun `the registry names at least one entity type`() {
        assertFailsWith<IllegalArgumentException> {
            ClientIdX509TrustRegistry(TrustDecisionEvaluator { decision() }, expectedEntityTypes = emptySet())
        }
    }

    /** Through `java.util.Set`, as a Java caller would: Kotlin's read-only `Set` type does not stop it. */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    @Test
    fun `wallets do not share entity types, and a caller cannot change them after construction`() {
        val evaluator = TrustDecisionEvaluator { decision() }
        val first = ClientIdX509TrustRegistry(evaluator)
        val second = ClientIdX509TrustRegistry(evaluator)
        val callerTypes = mutableSetOf(TrustedEntityType.RELYING_PARTY_PROVIDER)
        val fromCaller = ClientIdX509TrustRegistry(evaluator, expectedEntityTypes = callerTypes)

        for (types in listOf(ClientIdX509TrustRegistry.RELYING_PARTY_ENTITY_TYPES, first.expectedEntityTypes, fromCaller.expectedEntityTypes)) {
            val javaSet = types as java.util.Set<TrustedEntityType>
            assertFailsWith<UnsupportedOperationException> { javaSet.remove(TrustedEntityType.RELYING_PARTY_PROVIDER) }
            assertFailsWith<UnsupportedOperationException> { javaSet.add(TrustedEntityType.PID_PROVIDER) }
            assertFailsWith<UnsupportedOperationException> { javaSet.iterator().apply { next() }.remove() }
        }
        callerTypes.clear()

        val relyingParty = setOf(TrustedEntityType.RELYING_PARTY_PROVIDER, TrustedEntityType.ACCESS_CERTIFICATE_PROVIDER)
        assertEquals(relyingParty, second.expectedEntityTypes)
        assertEquals(relyingParty, ClientIdX509TrustRegistry(evaluator).expectedEntityTypes)
        assertEquals(setOf(TrustedEntityType.RELYING_PARTY_PROVIDER), fromCaller.expectedEntityTypes)
    }

    @Test
    fun `with pins and a registry, a registry denial does not fall through to a pin`() = runTest {
        val presented = presented()
        val pins = InMemoryTrustStore(listOf(presented.chain.last()))
        val evaluator = RecordingEvaluator(decision(code = TrustDecisionCode.NOT_TRUSTED))
        val trust = ClientIdTrustConfiguration(
            x509TrustAnchors = pins,
            x509TrustRegistry = ClientIdX509TrustRegistry(evaluator),
        )

        // The pins alone would accept this chain.
        assertIs<ClientValidationResult.Success>(authenticateSanDns(presented, ClientIdTrustConfiguration(x509TrustAnchors = pins)))
        for (failure in listOf(authenticateSanDns(presented, trust), authenticateHash(presented, trust))) {
            assertEquals(ClientIdError.X509TrustAnchorMismatch, assertIs<ClientValidationResult.Failure>(failure).error)
        }
        assertEquals(2, evaluator.requests.size)
    }

    @Test
    fun `with pins and a registry, a verifier_attestation x5c chain is checked against the pins`() = runTest {
        val attesterHost = "attester.example.com"
        val (rootKey, root) = newRoot()
        val attesterKey = SanDnsTests.genKey("attester-leaf")
        val attesterLeaf = X509CertificateUtil.createCertificate(rootKey, root, sigAlg) {
            subjectDn = "cn=$attesterHost"
            subjectPublicKey(attesterKey)
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
            extensionExtendedKeyUsage { addKeyUsage("1.3.6.1.5.5.7.3.2") }
            extensionSan { addDnsName(attesterHost) }
        }
        val verifierKey = JWKKey.generate(KeyType.Ed25519)
        val clientIdValue = "verifier.example"
        val attestation = CompactJws.sign(
            Json.encodeToString(
                buildJsonObject {
                    put("iss", "https://$attesterHost")
                    put("sub", clientIdValue)
                    put("exp", Clock.System.now().epochSeconds + 300)
                    put("cnf", buildJsonObject { put("jwk", verifierKey.getPublicKey().exportJWKObject()) })
                }
            ).encodeToByteArray(),
            attesterKey,
            JwsAlgorithm.ES256,
            buildJsonObject {
                put("typ", "verifier-attestation+jwt")
                put("x5c", JsonArray(listOf(attesterLeaf, root).map { JsonPrimitive(Base64.encode(it.encodedDer.toByteArray())) }))
            },
        )
        val clientId = VerifierAttestation(clientIdValue, clientIdValue)
        val context = RequestContext(
            clientId = clientIdValue,
            clientMetadataString = metadataJson,
            requestObjectJws = verifierKey.signJws("{}".encodeToByteArray(), mapOf("jwt" to JsonPrimitive(attestation))),
            responseUri = "https://verifier.example/response",
        )
        val evaluator = RecordingEvaluator(decision(code = TrustDecisionCode.NOT_TRUSTED))
        fun trust(pins: InMemoryTrustStore?) = ClientIdTrustConfiguration(
            x509TrustAnchors = pins,
            trustedVerifierAttestationIssuers = setOf("https://$attesterHost"),
            x509TrustRegistry = ClientIdX509TrustRegistry(evaluator),
        )

        assertIs<ClientValidationResult.Success>(
            clientId.authenticateVerifierAttestation(clientId, context, trust(InMemoryTrustStore(listOf(root))))
        )
        assertIs<ClientValidationResult.Failure>(clientId.authenticateVerifierAttestation(clientId, context, trust(null)))
        assertTrue(evaluator.requests.isEmpty(), "registry was asked ${evaluator.requests.size} times")
    }
}
