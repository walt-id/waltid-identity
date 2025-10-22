package id.walt.x509

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.*
import java.util.*
import kotlin.collections.ArrayList


fun CertificateDer.toX509(): X509Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(this.bytes)) as X509Certificate

actual fun parseX5cBase64(x5cBase64: List<String>): List<CertificateDer> =
    x5cBase64.map { CertificateDer(Base64.getDecoder().decode(it)) }

@Throws(X509ValidationException::class)
actual fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>?,
    enableRevocation: Boolean
) {
    try {

        val leafCert =  CertificateDer(leaf.bytes).toX509()
        val all = chain.map { it.toX509() }.toMutableList()

        // Remove leaf if passed both as leaf and in chain
        all.removeIf { it == leafCert }

        // Trust anchors from a provided list or from self-signed in chain
        val anchors = buildTrustAnchors(trustAnchors, all)
        if (anchors.isEmpty()) {
            throw X509ValidationException(
                "No trust anchors available: provide trustAnchors or include a trusted root."
            )
        }

        // Intermediates (non-self-signed)
        val intermediates = all.filterNot { isSelfSigned(it) }

        val selector = X509CertSelector().apply { certificate = leafCert }
        val store = CertStore.getInstance(
            "Collection",
            CollectionCertStoreParameters(intermediates)
        )

        val params = PKIXBuilderParameters(anchors, selector).apply {
            addCertStore(store)
            isRevocationEnabled = enableRevocation // requires JVM flags for OCSP/CRL.
        }

        val builder = CertPathBuilder.getInstance("PKIX")
        val result = builder.build(params) as PKIXCertPathBuilderResult

        val validator = CertPathValidator.getInstance("PKIX")
        validator.validate(result.certPath, params)
    } catch (e: CertPathBuilderException) {
        throw X509ValidationException("Certificate path could not be built: ${e.message}", e)
    } catch (e: CertPathValidatorException) {
        throw X509ValidationException("Certificate path invalid: ${e.message}", e)
    } catch (e: Exception) {
        throw X509ValidationException("Certificate validation failed: ${e.message}", e)
    }
}

private fun isSelfSigned(cert: X509Certificate): Boolean =
    try {
        cert.verify(cert.publicKey)
        cert.issuerX500Principal == cert.subjectX500Principal
    } catch (_: Exception) {
        false
    }

/**
 * Build TrustAnchor set from provided DER roots or any self-signeds in chain.
 */
private fun buildTrustAnchors(
    trustAnchors: List<CertificateDer>?,
    chain: List<X509Certificate>
): Set<TrustAnchor> {
    val anchors = HashSet<TrustAnchor>()

    trustAnchors?.forEach { der ->
        anchors.add(TrustAnchor(der.toX509(), null))
    }

    if (anchors.isEmpty()) {
        chain.filter { isSelfSigned(it) }.forEach { root ->
            anchors.add(TrustAnchor(root, null))
        }
    }
    return anchors
}

/**
 * Optional helper (JVM-only) to load a trust store and convert to DER anchors.
 */
fun loadTrustAnchorsFromKeyStore(ks: KeyStore): List<CertificateDer> {
    val list = ArrayList<CertificateDer>()
    val aliases = ks.aliases()
    while (aliases.hasMoreElements()) {
        val alias = aliases.nextElement()
        val cert = ks.getCertificate(alias) as? X509Certificate ?: continue
        list.add(CertificateDer(cert.encoded))
    }
    return list
}
