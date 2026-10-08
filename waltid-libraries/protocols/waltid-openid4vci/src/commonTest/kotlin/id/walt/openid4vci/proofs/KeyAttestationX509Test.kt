package id.walt.openid4vci.proofs

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.*
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.attestation.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlin.time.Clock

class KeyAttestationX509Test {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))
    private val context = CredentialProofValidationContext("https://issuer.example")
    private val configuration = CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity")
    private suspend fun payload(key: Key) = buildJsonObject {
        put("iat", Clock.System.now().epochSeconds)
        put("exp", Clock.System.now().epochSeconds + 7200)
        put("attested_keys", JsonArray(listOf(key.exportPublicJwkObject())))
    }.toString().encodeToByteArray()

    @Test
    fun `trusted chain with more than eight certificates is accepted`() = runTest {
        val rootKey = key("root")
        val leafKey = key("leaf")
        val signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val root = X509CertificateUtil.createSelfSignedCertificate(rootKey, signatureAlgorithm) {
            subjectDn = "CN=Long Chain Root"
            extensionBasicConstraints { cA = true }
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign) }
        }
        val chain = mutableListOf(root)
        var issuerKey = rootKey
        repeat(7) { index ->
            val intermediateKey = key("intermediate-$index")
            chain += X509CertificateUtil.createCertificate(issuerKey, chain.last(), signatureAlgorithm) {
                subjectDn = "CN=Intermediate $index"
                subjectPublicKey(intermediateKey)
                extensionBasicConstraints { cA = true }
                extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign) }
            }
            issuerKey = intermediateKey
        }
        chain += X509CertificateUtil.createCertificate(issuerKey, chain.last(), signatureAlgorithm) {
            subjectDn = "CN=Long Chain Attester"
            subjectPublicKey(leafKey)
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
        }
        val header = buildJsonObject {
            put("typ", "key-attestation+jwt")
            put("x5c", JsonArray(chain.asReversed().map { JsonPrimitive(Base64.encode(it.encodedDer.toByteArray())) }))
        }
        val jwt = CompactJws.sign(payload(leafKey), leafKey, JwsAlgorithm.ES256, header)
        val options = KeyAttestationConfig(KeyAttestationVerificationMethod.X509Chain(listOf(root.encodedPem))).toVerificationOptions()
        val evidence = KeyAttestationVerifier().verify(jwt, ProofTypeMetadata(setOf("ES256")), context, configuration, options)
        assertEquals(9, chain.size)
        assertEquals(Jwk.sha256Thumbprint(leafKey.exportPublicJwk()), Jwk.sha256Thumbprint(evidence.attesterKey.exportPublicJwk()))
    }

    @Test
    fun `duplicate subject cannot substitute an untrusted leaf`() = runTest {
        val rootKey = key("root")
        val leafKey = key("leaf")
        val attackerKey = key("attacker")
        val alg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val root = X509CertificateUtil.createSelfSignedCertificate(rootKey, alg) {
            subjectDn = "CN=Regression Root"
            extensionBasicConstraints { cA = true }
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign) }
        }
        val leaf = X509CertificateUtil.createCertificate(rootKey, root, alg) {
            subjectDn = "CN=Regression Attester"
            subjectPublicKey(leafKey)
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
        }
        val impostor = X509CertificateUtil.createSelfSignedCertificate(attackerKey, alg) {
            subjectDn = "CN=Regression Attester"
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
        }
        val header = buildJsonObject {
            put("typ", "key-attestation+jwt")
            put("x5c", JsonArray(listOf(impostor, leaf, root).map { JsonPrimitive(Base64.encode(it.encodedDer.toByteArray())) }))
        }
        val jwt = CompactJws.sign(payload(attackerKey), attackerKey, JwsAlgorithm.ES256, header)
        val options = KeyAttestationConfig(KeyAttestationVerificationMethod.X509Chain(listOf(root.encodedPem))).toVerificationOptions()
        assertFailsWith<CredentialProofValidationException> {
            KeyAttestationVerifier().verify(jwt, ProofTypeMetadata(setOf("ES256")), context, configuration, options)
        }
    }

    @Test
    fun `trust requires a valid chain and a signature from its leaf regardless of kid or issuer`() = runTest {
        val rootKey = key("root")
        val leafKey = key("leaf")
        val attackerKey = key("attacker")
        val signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        suspend fun root(key: Key) = X509CertificateUtil.createSelfSignedCertificate(key, signatureAlgorithm) {
            subjectDn = "CN=Attestation Root ${key.id.value}"
            extensionBasicConstraints { cA = true }
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign) }
        }
        val root = root(rootKey)
        val leaf = X509CertificateUtil.createCertificate(rootKey, root, signatureAlgorithm) {
            subjectDn = "CN=Attester"
            subjectPublicKey(leafKey)
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
        }
        val header = buildJsonObject {
            put("typ", "key-attestation+jwt")
            put("kid", "untrusted-hint")
            put("x5c", JsonArray(listOf(leaf, root).map { JsonPrimitive(Base64.encode(it.encodedDer.toByteArray())) }))
        }
        val payload = buildJsonObject {
            put("iat", Clock.System.now().epochSeconds)
            put("exp", Clock.System.now().epochSeconds + 300)
            put("iss", "untrusted-hint")
            put("nonce", "test-nonce")
            put("attested_keys", JsonArray(listOf(attackerKey.exportPublicJwkObject())))
        }.toString().encodeToByteArray()
        val jwt = CompactJws.sign(payload, leafKey, JwsAlgorithm.ES256, header)
        val options = KeyAttestationConfig(KeyAttestationVerificationMethod.X509Chain(listOf(root.encodedPem))).toVerificationOptions()
        val verifier = KeyAttestationVerifier()
        val context = CredentialProofValidationContext("https://issuer.example")
        val configuration = CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity")
        val evidence = verifier.verify(jwt, ProofTypeMetadata(setOf("ES256")), context, configuration, options)
        assertEquals(Jwk.sha256Thumbprint(leafKey.exportPublicJwk()), Jwk.sha256Thumbprint(evidence.attesterKey.exportPublicJwk()))
        val nonceService = object : CredentialNonceService {
            override suspend fun issue(binding: CredentialNonceBinding) = IssuedCredentialNonce("test-nonce")
            override suspend fun validate(nonce: String, binding: CredentialNonceBinding) =
                if (nonce == "test-nonce") CredentialNonceValidationResult.VALID else CredentialNonceValidationResult.INVALID
        }
        val standaloneContext = context.copy(nonceValidation = CredentialNonceValidationContext(nonceService,
            CredentialNonceBinding(context.credentialIssuer, "https://issuer.example/credential", "https://issuer.example/nonce")))
        val standalone = verifier.verify(jwt, ProofTypeMetadata(setOf("ES256")), standaloneContext, configuration, options,
            KeyAttestationUsage.STANDALONE_PROOF)
        assertEquals(Jwk.sha256Thumbprint(attackerKey.exportPublicJwk()), Jwk.sha256Thumbprint(standalone.attestedKeys.single().exportPublicJwk()))
        // The shared builder can omit a disconnected cycle. The attestation
        // adapter must reject it even when the remaining leaf/root path is trusted.
        val keyA = key("cycle-a")
        val keyB = key("cycle-b")
        val initialA = X509CertificateUtil.createSelfSignedCertificate(keyA, signatureAlgorithm) {
            subjectDn = "CN=Cycle A"
        }
        val certificateB = X509CertificateUtil.createCertificate(keyA, initialA, signatureAlgorithm) {
            subjectDn = "CN=Cycle B"
            subjectPublicKey(keyB)
        }
        val certificateA = X509CertificateUtil.createCertificate(keyB, certificateB, signatureAlgorithm) {
            subjectDn = "CN=Cycle A"
            subjectPublicKey(keyA)
        }
        for (certificates in listOf(emptyList(), listOf(root, leaf), listOf(leaf, root, leaf), listOf(leaf, root, certificateA, certificateB))) {
            val malformedHeader = JsonObject(header + ("x5c" to JsonArray(certificates.map {
                JsonPrimitive(Base64.encode(it.encodedDer.toByteArray()))
            })))
            val malformed = CompactJws.sign(payload, leafKey, JwsAlgorithm.ES256, malformedHeader)
            assertFailsWith<CredentialProofValidationException> { verifier.verify(malformed, null, context, configuration, options) }
        }
        val forged = CompactJws.sign(payload, attackerKey, JwsAlgorithm.ES256, header)
        assertFailsWith<CredentialProofValidationException> { verifier.verify(forged, null, context, configuration, options) }
        assertFailsWith<CredentialProofValidationException> {
            verifier.verify(forged, null, standaloneContext, configuration, options, KeyAttestationUsage.STANDALONE_PROOF)
        }
        val untrusted = KeyAttestationConfig(KeyAttestationVerificationMethod.X509Chain(listOf(root(attackerKey).encodedPem))).toVerificationOptions()
        assertFailsWith<CredentialProofValidationException> { verifier.verify(jwt, null, context, configuration, untrusted) }
    }
}
