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
import id.walt.openid4vp.clientidprefix.*
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.openid4vp.clientidprefix.prefixes.X509SanDns
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * WAL-1509: two pinned trust anchors share a subject DN but have different keys (key rollover).
 * Chains to either one must authenticate, a chain to a third same-DN CA that is not pinned must not.
 */
class SameSubjectDnAnchorsTests {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
    private val dnsName = "verifier.example.com"
    private val metadataJson = """{ "vp_formats_supported": {} }"""

    private class Ca(val key: Key, val cert: X509Certificate)

    private class Presented(val requestObject: String, val leaf: X509Certificate)

    private suspend fun newCa(id: String): Ca {
        val key = SanDnsTests.genKey(id)
        val cert = X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "cn=Rollover Root, o=Walt.id"
            extensionBasicConstraints { cA = true }
            extensionKeyUsage {
                addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign)
            }
        }
        return Ca(key, cert)
    }

    /** A request object signed by a leaf issued directly by [ca], with `x5c` = leaf only or leaf + [ca]. */
    private suspend fun presentedBy(ca: Ca, includeRoot: Boolean): Presented {
        val leafKey = SanDnsTests.genKey("leaf-${ca.cert.fingerprintSha256Hex}")
        val leaf = X509CertificateUtil.createCertificate(ca.key, ca.cert, sigAlg) {
            subjectDn = "cn=$dnsName"
            subjectPublicKey(leafKey)
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
            extensionExtendedKeyUsage { addKeyUsage("1.3.6.1.5.5.7.3.2") }
            extensionSan { addDnsName(dnsName) }
        }
        val chain = if (includeRoot) listOf(leaf, ca.cert) else listOf(leaf)
        val requestObject = CompactJws.sign(
            "{}".encodeToByteArray(),
            leafKey,
            JwsAlgorithm.ES256,
            buildJsonObject {
                put("x5c", JsonArray(chain.map { JsonPrimitive(Base64.Default.encode(it.encodedDer.toByteArray())) }))
            },
        )
        return Presented(requestObject, leaf)
    }

    private fun sanDnsContext(presented: Presented) = RequestContext(
        clientId = "x509_san_dns:$dnsName",
        clientMetadataString = metadataJson,
        requestObjectJws = presented.requestObject,
        responseUri = "https://$dnsName/response",
    )

    private fun hashContext(presented: Presented, clientId: X509Hash) = RequestContext(
        clientId = clientId.rawValue,
        clientMetadataString = metadataJson,
        requestObjectJws = presented.requestObject,
        responseUri = "https://$dnsName/response",
    )

    private fun hashClientId(presented: Presented): X509Hash {
        val raw = X509Hash.clientIdForCertificate(presented.leaf.encodedDer.toByteArray())
        return X509Hash(raw.removePrefix("x509_hash:"), raw)
    }

    @Test
    fun `x509_san_dns authenticates chains to either of two pinned same-DN anchors`() = runTest {
        val caA = newCa("san-a")
        val caB = newCa("san-b")
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(caA.cert, caB.cert)))
        val clientId = X509SanDns(dnsName, "x509_san_dns:$dnsName")

        for (ca in listOf(caA, caB)) {
            for (includeRoot in listOf(false, true)) {
                val presented = presentedBy(ca, includeRoot)
                val result = clientId.authenticateX509SanDns(clientId, sanDnsContext(presented), trust)
                assertIs<ClientValidationResult.Success>(result, "includeRoot=$includeRoot: $result")
            }
        }
    }

    @Test
    fun `x509_san_dns rejects chains to a same-DN anchor that is not pinned`() = runTest {
        val caA = newCa("san-a")
        val caB = newCa("san-b")
        val unpinned = newCa("san-unpinned")
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(caA.cert, caB.cert)))
        val clientId = X509SanDns(dnsName, "x509_san_dns:$dnsName")

        for (includeRoot in listOf(false, true)) {
            val presented = presentedBy(unpinned, includeRoot)
            val failure = assertIs<ClientValidationResult.Failure>(
                clientId.authenticateX509SanDns(clientId, sanDnsContext(presented), trust),
                "includeRoot=$includeRoot"
            )
            assertEquals(ClientIdError.InvalidSignature, failure.error, "includeRoot=$includeRoot")
        }
    }

    @Test
    fun `x509_hash authenticates chains to either of two pinned same-DN anchors`() = runTest {
        val caA = newCa("hash-a")
        val caB = newCa("hash-b")
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(caA.cert, caB.cert)))

        for (ca in listOf(caA, caB)) {
            for (includeRoot in listOf(false, true)) {
                val presented = presentedBy(ca, includeRoot)
                val clientId = hashClientId(presented)
                val result = clientId.authenticateX509Hash(clientId, hashContext(presented, clientId), trust)
                assertIs<ClientValidationResult.Success>(result, "includeRoot=$includeRoot: $result")
            }
        }
    }

    @Test
    fun `x509_hash rejects chains to a same-DN anchor that is not pinned`() = runTest {
        val caA = newCa("hash-a")
        val caB = newCa("hash-b")
        val unpinned = newCa("hash-unpinned")
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(caA.cert, caB.cert)))

        for (includeRoot in listOf(false, true)) {
            val presented = presentedBy(unpinned, includeRoot)
            val clientId = hashClientId(presented)
            val failure = assertIs<ClientValidationResult.Failure>(
                clientId.authenticateX509Hash(clientId, hashContext(presented, clientId), trust),
                "includeRoot=$includeRoot"
            )
            assertEquals(ClientIdError.InvalidSignature, failure.error, "includeRoot=$includeRoot")
        }
    }

    @Test
    fun `x509_san_dns fails closed when two pinned certificates for the same key make the issuer ambiguous`() = runTest {
        val ca = newCa("san-dup")
        val duplicate = X509CertificateUtil.createSelfSignedCertificate(ca.key, sigAlg) {
            subjectDn = "cn=Rollover Root, o=Walt.id"
            extensionBasicConstraints { cA = true }
        }
        val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(ca.cert, duplicate)))
        val clientId = X509SanDns(dnsName, "x509_san_dns:$dnsName")

        val failure = assertIs<ClientValidationResult.Failure>(
            clientId.authenticateX509SanDns(clientId, sanDnsContext(presentedBy(ca, includeRoot = false)), trust)
        )

        val error = assertIs<ClientIdError.AttestationError>(failure.error)
        assertTrue(error.toString().contains("Refusing to select one"), error.toString())
    }
}
