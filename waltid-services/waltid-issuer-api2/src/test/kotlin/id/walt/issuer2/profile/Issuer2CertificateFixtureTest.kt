package id.walt.issuer2.profile

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.loadIssuer2ConfigFiles
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.interfaces.ECPublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Issuer2CertificateFixtureTest {

    @AfterEach
    fun clearConfig() = clearIssuer2TestEnvironment()

    @Test
    fun `issuer2 certificate fixtures use purpose-specific AT leaves`() {
        loadIssuer2ConfigFiles(baseUrlOverride = null)
        val profilesConfig = ConfigManager.getConfig<Issuer2ProfilesConfig>()
        val conformanceCertificateDir = conformanceCertificateDir()
        val root = readCertificate(
            Files.readString(conformanceCertificateDir.resolve("issuer2-haip-root-ca.pem")),
        )

        assertTrue(root.subjectX500Principal.name.contains("C=AT"))
        assertEquals(0, root.basicConstraints)
        assertTrue(assertNotNull(root.keyUsage)[5], "IACA must allow certificate signing")
        assertTrue(assertNotNull(root.keyUsage)[6], "IACA must allow CRL signing")

        assertEquals(
            Files.readString(conformanceCertificateDir.resolve("issuer2-haip-leaf.pem")).trim(),
            profilesConfig.defaultHaipIssuerX5chain.single().trim(),
            "The conformance runner leaf must match issuer2's HAIP SD-JWT leaf",
        )

        assertLeaf(
            profilesConfig.defaultMdocIssuerX5chain.single(),
            "defaultMdocIssuerX5chain",
            root,
            expectedX = DEFAULT_KEY_X,
            expectedY = DEFAULT_KEY_Y,
            mdoc = true,
        )
        assertLeaf(
            profilesConfig.defaultIssuerX5chain.single(),
            "defaultIssuerX5chain",
            root,
            expectedX = DEFAULT_KEY_X,
            expectedY = DEFAULT_KEY_Y,
            mdoc = false,
        )
        assertLeaf(
            profilesConfig.defaultHaipMdocIssuerX5chain.single(),
            "defaultHaipMdocIssuerX5chain",
            root,
            expectedX = HAIP_KEY_X,
            expectedY = HAIP_KEY_Y,
            mdoc = true,
        )
        assertLeaf(
            profilesConfig.defaultHaipIssuerX5chain.single(),
            "defaultHaipIssuerX5chain",
            root,
            expectedX = HAIP_KEY_X,
            expectedY = HAIP_KEY_Y,
            mdoc = false,
        )
    }

    private fun assertLeaf(
        pem: String,
        label: String,
        root: X509Certificate,
        expectedX: String,
        expectedY: String,
        mdoc: Boolean,
    ) {
        val leaf = readCertificate(pem)
        leaf.verify(root.publicKey)

        assertTrue(leaf.subjectX500Principal.name.contains("C=AT"), "$label must use AT")
        assertFalse(leaf.subjectX500Principal == leaf.issuerX500Principal, "$label must not be self-signed")
        assertEquals(-1, leaf.basicConstraints, "$label must be an end-entity certificate")
        assertTrue(assertNotNull(leaf.keyUsage)[0], "$label must allow digital signatures")
        assertTrue(leaf.criticalExtensionOIDs.contains(KEY_USAGE_OID), "$label key usage must be critical")
        assertNotNull(leaf.getExtensionValue(SUBJECT_KEY_IDENTIFIER_OID), "$label must contain SKI")
        assertNotNull(leaf.getExtensionValue(AUTHORITY_KEY_IDENTIFIER_OID), "$label must contain AKI")
        assertNotNull(leaf.getExtensionValue(ISSUER_ALT_NAME_OID), "$label must contain issuerAltName")
        assertNotNull(leaf.getExtensionValue(CRL_DISTRIBUTION_POINTS_OID), "$label must contain a CRL distribution point")

        val remaining = Duration.between(Instant.now(), leaf.notAfter.toInstant())
        assertTrue(remaining > Duration.ofDays(30), "$label expires in fewer than 30 days")
        assertTrue(
            Duration.between(leaf.notBefore.toInstant(), leaf.notAfter.toInstant()) <= Duration.ofDays(457),
            "$label exceeds the ISO mdoc maximum leaf validity",
        )

        val extendedKeyUsage = leaf.extendedKeyUsage.orEmpty()
        if (mdoc) {
            assertTrue(MDOC_DOCUMENT_SIGNER_EKU in extendedKeyUsage, "$label must contain the mdoc DS EKU")
            assertTrue(leaf.criticalExtensionOIDs.contains(EXTENDED_KEY_USAGE_OID), "$label EKU must be critical")
        } else {
            assertFalse(MDOC_DOCUMENT_SIGNER_EKU in extendedKeyUsage, "$label must not use the mdoc DS EKU")
            assertFalse(TLS_CLIENT_AUTH_EKU in extendedKeyUsage, "$label must not use the TLS client-auth EKU")
        }

        val publicKey = leaf.publicKey as ECPublicKey
        assertEquals(expectedX, publicKey.w.affineX.toBase64Url(32), "$label public key x")
        assertEquals(expectedY, publicKey.w.affineY.toBase64Url(32), "$label public key y")
    }

    private fun readCertificate(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate

    private fun java.math.BigInteger.toBase64Url(size: Int): String {
        val unsigned = toByteArray().let { if (it.size > size) it.copyOfRange(it.size - size, it.size) else it }
        val padded = ByteArray(size)
        unsigned.copyInto(padded, destinationOffset = size - unsigned.size)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(padded)
    }

    private fun conformanceCertificateDir(): Path = listOf(
        Path.of("../waltid-openid4vp-conformance-runners/src/test/resources/certs"),
        Path.of("waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs"),
        Path.of("waltid-identity/waltid-services/waltid-openid4vp-conformance-runners/src/test/resources/certs"),
    ).map { it.toAbsolutePath().normalize() }
        .firstOrNull { Files.isRegularFile(it.resolve("issuer2-haip-root-ca.pem")) }
        ?: error("Could not locate issuer2 HAIP conformance certificates")

    private companion object {
        const val DEFAULT_KEY_X = "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM"
        const val DEFAULT_KEY_Y = "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
        const val HAIP_KEY_X = "4GbYM-GfrL8u9J4bPUMd21CiXH2t6PDWVcepxhPtopU"
        const val HAIP_KEY_Y = "a2Z7QWgvLz0nl4KOjstBcowX47VmhUQgaJi_8cMCqas"
        const val KEY_USAGE_OID = "2.5.29.15"
        const val EXTENDED_KEY_USAGE_OID = "2.5.29.37"
        const val SUBJECT_KEY_IDENTIFIER_OID = "2.5.29.14"
        const val AUTHORITY_KEY_IDENTIFIER_OID = "2.5.29.35"
        const val ISSUER_ALT_NAME_OID = "2.5.29.18"
        const val CRL_DISTRIBUTION_POINTS_OID = "2.5.29.31"
        const val MDOC_DOCUMENT_SIGNER_EKU = "1.0.18013.5.1.2"
        const val TLS_CLIENT_AUTH_EKU = "1.3.6.1.5.5.7.3.2"
    }
}
