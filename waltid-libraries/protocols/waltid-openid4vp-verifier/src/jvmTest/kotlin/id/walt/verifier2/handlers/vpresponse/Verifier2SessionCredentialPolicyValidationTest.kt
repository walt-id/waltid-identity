package id.walt.verifier2.handlers.vpresponse

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.signatures.SdJwtCredentialSignature
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.X509TrustPolicy
import id.walt.verifier2.data.Verification2Session
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Verifier2SessionCredentialPolicyValidationTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun signaturePolicyAloneAcceptsX5cIssuerKey() = runTest {
        val (_, credential) = x5cSdJwtUnderRoot()
        val results = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(
            policies = Verification2Session.DefinedVerificationPolicies(
                vc_policies = VCPolicyList(listOf(CredentialSignaturePolicy())),
            ),
            validatedCredentials = mapOf("pid" to listOf(credential)),
        )

        assertTrue(results.vcPolicies.single().success, results.vcPolicies.toString())
    }

    @Test
    fun signatureWithX509TrustAcceptsAnchoredX5cIssuerKey() = runTest {
        val (root, credential) = x5cSdJwtUnderRoot()
        val results = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(
            policies = Verification2Session.DefinedVerificationPolicies(
                vc_policies = VCPolicyList(listOf(CredentialSignaturePolicy(), X509TrustPolicy(listOf(root.encodedPem)))),
            ),
            validatedCredentials = mapOf("pid" to listOf(credential)),
        )

        assertTrue(results.vcPolicies.all { it.success }, results.vcPolicies.toString())
    }

    @Test
    fun x509TrustWithWrongRootFailsOnTrustNotSignature() = runTest {
        val (_, credential) = x5cSdJwtUnderRoot()
        val (otherRoot, _) = x5cSdJwtUnderRoot()
        val results = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(
            policies = Verification2Session.DefinedVerificationPolicies(
                vc_policies = VCPolicyList(listOf(CredentialSignaturePolicy(), X509TrustPolicy(listOf(otherRoot.encodedPem)))),
            ),
            validatedCredentials = mapOf("pid" to listOf(credential)),
        )

        assertTrue(results.vcPolicies.single { it.policy is CredentialSignaturePolicy }.success)
        assertFalse(results.vcPolicies.single { it.policy is X509TrustPolicy }.success)
    }

    private suspend fun generateKey(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    /** An SD-JWT VC signed by a leaf certificate in its `x5c`, issued under a fresh root CA (returned first). */
    private suspend fun x5cSdJwtUnderRoot(): Pair<X509Certificate, DigitalCredential> {
        val rootKey = generateKey("root-key")
        val root = X509CertificateUtil.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "CN=Root CA"
        }
        val key = generateKey("issuer-key")
        val certificate = X509CertificateUtil.createCertificate(rootKey, root, sigAlg) {
            subjectDn = "CN=Credential issuer"
            subjectPublicKey(key)
            extensionSan { addDnsName("issuer.example") }
        }
        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", "https://issuer.example/identity-credential")
            put("_sd_alg", "sha-256")
        }
        val signed = CompactJws.sign(
            payload = Json.encodeToString(payload).encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "dc+sd-jwt")
                put("x5c", JsonArray(listOf(JsonPrimitive(Base64.encode(certificate.encodedDer.toByteArray())))))
            },
        )
        return root to SdJwtCredential(
            dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
            credentialData = payload,
            issuer = "https://issuer.example",
            signature = SdJwtCredentialSignature(signed, CompactJws.decodeUnverified(signed).protectedHeader),
            signed = signed,
        )
    }
}
