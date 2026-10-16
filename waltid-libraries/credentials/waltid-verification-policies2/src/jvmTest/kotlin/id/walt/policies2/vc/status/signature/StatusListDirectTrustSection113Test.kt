package id.walt.policies2.vc.status.signature

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.credentials.formats.W3C11
import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.policies2.vc.policies.status.signature.StatusListEmbeddedKeyReference
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizationRequest
import id.walt.cose.CoseCertHash
import id.walt.policies2.vc.policies.status.signature.authorizeStatusListSigner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.test.assertContains

class StatusListDirectTrustSection113Test {
    private val provider = CryptographySoftwareKeyProvider()
    private val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    @Test
    fun `x5t#S256 reference accepts the credential key when the certificate bytes differ`() = runTest {
        val material = issuedMaterial()
        val renewed = issued(
            subjectKey = material.issuerKey,
            issuerKey = material.caKey,
            issuer = material.ca,
            dn = "CN=Renewed issuer, O=Walt.id",
        )
        authorizeStatusListSigner(
            request(
                credential = material.credentialLeaf,
                credentialChain = listOf(material.credentialLeaf, material.ca),
                statusChain = listOf(renewed),
                verificationKey = publicKey(renewed),
                reference = StatusListEmbeddedKeyReference(x5tSha256 = sha256(material.credentialLeaf.encodedDer.toByteArray())),
            ),
            authorizer = null,
        )
    }

    @Test
    fun `same public key without an x5t or kid reference is rejected`() = runTest {
        val material = issuedMaterial()
        val renewed = issued(
            subjectKey = material.issuerKey,
            issuerKey = material.caKey,
            issuer = material.ca,
            dn = "CN=Renewed issuer, O=Walt.id",
        )
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request(
                    credential = material.credentialLeaf,
                    credentialChain = listOf(material.credentialLeaf, material.ca),
                    statusChain = listOf(renewed),
                    verificationKey = publicKey(renewed),
                ),
                authorizer = null,
            )
        }
        assertContains(error.message!!, "status-list x5c leaf does not match")
    }

    @Test
    fun `x5t reference accepts the credential certificate`() = runTest {
        val material = issuedMaterial()
        authorizeStatusListSigner(
            request(
                credential = material.credentialLeaf,
                credentialChain = listOf(material.credentialLeaf),
                statusChain = emptyList(),
                verificationKey = publicKey(material.credentialLeaf),
                reference = StatusListEmbeddedKeyReference(x5tSha1 = sha1(material.credentialLeaf.encodedDer.toByteArray())),
            ),
            authorizer = null,
        )
    }

    @Test
    fun `x5t#S256 of a different certificate is rejected`() = runTest {
        val material = issuedMaterial()
        val other = issued(
            subjectKey = material.statusKey,
            issuerKey = material.caKey,
            issuer = material.ca,
            dn = "CN=Other, O=Walt.id",
        )
        assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request(
                    credential = material.credentialLeaf,
                    credentialChain = listOf(material.credentialLeaf),
                    statusChain = listOf(other),
                    verificationKey = publicKey(other),
                    reference = StatusListEmbeddedKeyReference(x5tSha256 = sha256(other.encodedDer.toByteArray())),
                ),
                authorizer = null,
            )
        }
    }

    @Test
    fun `matching kid accepts the credential key`() = runTest {
        val material = issuedMaterial()
        authorizeStatusListSigner(
            request(
                credential = material.credentialLeaf,
                credentialChain = listOf(material.credentialLeaf),
                statusChain = emptyList(),
                verificationKey = publicKey(material.credentialLeaf),
                credentialKid = "issuer-key",
                statusListKid = "issuer-key",
            ),
            authorizer = null,
        )
    }

    @Test
    fun `matching kid with a different public key is rejected`() = runTest {
        val material = issuedMaterial()
        assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request(
                    credential = material.credentialLeaf,
                    credentialChain = listOf(material.credentialLeaf),
                    statusChain = emptyList(),
                    verificationKey = publicKey(material.statusLeaf),
                    credentialKid = "issuer-key",
                    statusListKid = "issuer-key",
                ),
                authorizer = null,
            )
        }
    }

    @Test
    fun `COSE x5t SHA-256 reference accepts the credential certificate`() = runTest {
        val material = issuedMaterial()
        authorizeStatusListSigner(
            request(
                credential = material.credentialLeaf,
                credentialChain = listOf(material.credentialLeaf),
                statusChain = emptyList(),
                verificationKey = publicKey(material.credentialLeaf),
                reference = StatusListEmbeddedKeyReference(
                    coseHashAlgorithm = CoseCertHash.SHA_256,
                    coseHash = sha256(material.credentialLeaf.encodedDer.toByteArray()),
                ),
            ),
            authorizer = null,
        )
    }

    @Test
    fun `status certificate with the oauth status signing EKU from the same CA is accepted`() = runTest {
        val material = issuedMaterial(statusEku = setOf(OAUTH_STATUS_SIGNING_EKU))
        authorizeStatusListSigner(
            request(
                credential = material.credentialLeaf,
                credentialChain = listOf(material.credentialLeaf, material.ca),
                statusChain = listOf(material.statusLeaf),
                verificationKey = publicKey(material.statusLeaf),
            ),
            authorizer = null,
        )
    }

    @Test
    fun `same CA without the status signing EKU is rejected`() = runTest {
        val material = issuedMaterial()
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request(
                    credential = material.credentialLeaf,
                    credentialChain = listOf(material.credentialLeaf, material.ca),
                    statusChain = listOf(material.statusLeaf),
                    verificationKey = publicKey(material.statusLeaf),
                ),
                authorizer = null,
            )
        }
        assertContains(error.message!!, "status-list x5c leaf does not match")
    }

    @Test
    fun `status signing EKU from a different CA is rejected`() = runTest {
        val material = issuedMaterial()
        val otherCaKey = generate("other-ca")
        val otherCa = selfSigned("CN=Other CA, O=Walt.id", otherCaKey)
        val foreignStatus = issued(
            subjectKey = material.statusKey,
            issuerKey = otherCaKey,
            issuer = otherCa,
            dn = "CN=Foreign status, O=Walt.id",
            eku = setOf(OAUTH_STATUS_SIGNING_EKU),
        )
        assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request(
                    credential = material.credentialLeaf,
                    credentialChain = listOf(material.credentialLeaf, material.ca),
                    statusChain = listOf(foreignStatus, otherCa),
                    verificationKey = publicKey(foreignStatus),
                ),
                authorizer = null,
            )
        }
    }

    private suspend fun issuedMaterial(statusEku: Set<String> = emptySet()): Material {
        val caKey = generate("ca")
        val issuerKey = generate("issuer")
        val statusKey = generate("status")
        val ca = selfSigned("CN=Status CA, O=Walt.id", caKey)
        return Material(
            caKey = caKey,
            ca = ca,
            issuerKey = issuerKey,
            statusKey = statusKey,
            credentialLeaf = issued(
                subjectKey = issuerKey,
                issuerKey = caKey,
                issuer = ca,
                dn = "CN=Credential issuer, O=Walt.id",
            ),
            statusLeaf = issued(
                subjectKey = statusKey,
                issuerKey = caKey,
                issuer = ca,
                dn = "CN=Status issuer, O=Walt.id",
                eku = statusEku,
            ),
        )
    }

    private suspend fun selfSigned(dn: String, key: Key): X509Certificate =
        X509CertificateUtil.createSelfSignedCertificate(key, algorithm) {
            subjectDn = dn
        }

    private suspend fun issued(
        subjectKey: Key,
        issuerKey: Key,
        issuer: X509Certificate,
        dn: String,
        eku: Set<String> = emptySet(),
    ): X509Certificate = X509CertificateUtil.createCertificate(issuerKey, issuer, algorithm) {
        subjectDn = dn
        subjectPublicKey(subjectKey)
        if (eku.isNotEmpty()) {
            extensionExtendedKeyUsage { addKeyUsage(*eku.toTypedArray()) }
        }
    }

    private suspend fun publicKey(certificate: X509Certificate): Key =
        certificate.restoreSubjectPublicKey(X509CertificateUtil.services.cryptoRuntime)

    private suspend fun generate(id: String): Key = provider.generate(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ),
    )

    private fun request(
        credential: X509Certificate,
        credentialChain: List<X509Certificate>,
        statusChain: List<X509Certificate>,
        verificationKey: Key,
        reference: StatusListEmbeddedKeyReference? = null,
        credentialKid: String? = null,
        statusListKid: String? = null,
    ): StatusListSignerAuthorizationRequest = StatusListSignerAuthorizationRequest(
        referencedCredential = W3C11(
            credentialData = buildJsonObject {},
            signature = JwtCredentialSignature(
                signature = "test",
                jwtHeader = buildJsonObject {
                    put("x5c", JsonArray(credentialChain.map { JsonPrimitive(it.base64Der()) }))
                    credentialKid?.let { put("kid", it) }
                },
            ),
            signed = null,
            issuer = null,
        ),
        statusListUri = "https://status.example/list/1",
        signer = ResolvedJwtVerificationKey(
            key = verificationKey,
            source = JwtKeyResolutionSource.X5C,
            signerIdentifier = "https://issuer.example",
            keyId = statusListKid,
            certificateChain = statusChain.map { it.base64Der() },
        ),
        embeddedKeyReference = reference,
    )

    private fun X509Certificate.base64Der(): String = Base64.encode(encodedDer.toByteArray())

    private fun sha256(der: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(der)

    private fun sha1(der: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(der)

    private data class Material(
        val caKey: Key,
        val ca: X509Certificate,
        val issuerKey: Key,
        val statusKey: Key,
        val credentialLeaf: X509Certificate,
        val statusLeaf: X509Certificate,
    )

    companion object {
        private const val OAUTH_STATUS_SIGNING_EKU = "1.3.6.1.5.5.7.3.45"
    }
}
