package id.walt.issuer2.profile

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.config.registerIssuer2ConfigDecoders
import id.walt.issuer2.testsupport.MetadataCertificateFixture
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.loadIssuer2ConfigFiles
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningMethod
import id.walt.openid4vci.metadata.issuer.signing.SignedMetadataConfig
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPrivateKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class Issuer2CertificateFixtureTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @AfterEach
    fun clearConfig() = clearIssuer2TestEnvironment()

    @Test
    fun `EUDI credential profiles share the metadata signing key and certificate`() {
        val fixture = MetadataCertificateFixture()
        val fixturePublicKey = fixture.keyPair.public as ECPublicKey
        val fixturePrivateKey = fixture.keyPair.private as ECPrivateKey
        val fixtureKey = buildJsonObject {
            put("type", "jwk")
            putJsonObject("jwk") {
                put("kty", "EC")
                put("crv", "P-256")
                put("x", fixturePublicKey.w.affineX.toBase64Url(32))
                put("y", fixturePublicKey.w.affineY.toBase64Url(32))
                put("d", fixturePrivateKey.s.toBase64Url(32))
            }
        }
        val fixtureSigning = fixture.config()
        val profileIds = listOf("identityCredentialSdJwtEudi", "isoPhotoIdEudi", "eudiPidSdJwt", "eudiPidMdoc")
        val profileFile = temporaryDirectory.resolve("issuer2-profiles.conf")
        Files.writeString(profileFile, """
            defaultEudiIssuerKey = $fixtureKey
            defaultEudiIssuerX5chain = ${Json.encodeToString(fixtureSigning.certificateChainPem)}
            profiles {
                ${profileIds.joinToString("\n") { id -> """
                    $id {
                        name = "$id"
                        credentialConfigurationId = "$id"
                        issuerKey = ${'$'}{defaultEudiIssuerKey}
                        x5Chain = ${'$'}{defaultEudiIssuerX5chain}
                        credentialData = {}
                    }
                """ }}
            }
        """.trimIndent())
        val serviceFile = temporaryDirectory.resolve("issuer-service.conf")
        Files.writeString(serviceFile, """
            baseUrl = "https://issuer.example"
            signedMetadata = ${Json.encodeToString(SignedMetadataConfig.serializer(), SignedMetadataConfig(fixtureSigning))}
        """.trimIndent())
        clearIssuer2TestEnvironment()
        registerIssuer2ConfigDecoders()
        System.setProperty("config.file.issuer-service", serviceFile.toString())
        System.setProperty("config.file.issuer2-profiles", profileFile.toString())
        ConfigManager.registerConfig("issuer-service", Issuer2ServiceConfig::class)
        ConfigManager.registerConfig("issuer2-profiles", Issuer2ProfilesConfig::class)
        ConfigManager.loadConfigs()
        val profiles = ConfigManager.getConfig<Issuer2ProfilesConfig>()
        val metadata = assertIs<MetadataSigningMethod.X509Chain>(
            ConfigManager.getConfig<Issuer2ServiceConfig>().signedMetadata?.signingMethod,
        )
        val leaf = readCertificate(metadata.certificateChainPem.first())
        val privateKeyDer = Base64.getMimeDecoder().decode(metadata.privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""))
        val metadataKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKeyDer)) as ECPrivateKey
        assertEquals(metadata.certificateChainPem.map(String::trim), profiles.defaultEudiIssuerX5chain.map(String::trim))
        val key = assertNotNull(profiles.defaultEudiIssuerKey).getValue("jwk").jsonObject
        val scalar = BigInteger(1, Base64.getUrlDecoder().decode(key.getValue("d").jsonPrimitive.content))
        val publicKey = leaf.publicKey as ECPublicKey
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(scalar, publicKey.params))
        assertEquals(metadataKey.s, scalar)
        assertEquals(publicKey.w.affineX.toBase64Url(32), key.getValue("x").jsonPrimitive.content)
        assertEquals(publicKey.w.affineY.toBase64Url(32), key.getValue("y").jsonPrimitive.content)

        // Prove that the configured private key actually signs for the certificate's public key.
        val message = "OSS EUDI signing fixture".toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(message)
            sign()
        }
        assertTrue(Signature.getInstance("SHA256withECDSA").run {
            initVerify(leaf.publicKey)
            update(message)
            verify(signature)
        })
        for (id in profileIds) {
            val profile = profiles.profiles.getValue(id)
            assertEquals(profiles.defaultEudiIssuerKey, profile.issuerKey, "$id signing key")
            assertEquals(profiles.defaultEudiIssuerX5chain, profile.x5Chain, "$id certificate chain")
            assertNull(profile.issuerDid, "$id must not reference the old signing key's DID")
        }
    }

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
