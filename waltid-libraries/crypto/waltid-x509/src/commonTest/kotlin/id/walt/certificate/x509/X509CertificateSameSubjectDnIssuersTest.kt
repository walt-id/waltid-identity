package id.walt.certificate.x509

import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.truststore.CompositeTrustStore
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WAL-1509: several trusted CAs can share one subject DN (key rollover). The issuer of a certificate
 * must be selected by key, never by "first with that DN", and an ambiguous selection must fail closed.
 */
class X509CertificateSameSubjectDnIssuersTest {

    private class IssuingCa(val key: Key, val cert: X509Certificate)

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    private suspend fun newCa(
        id: String,
        dn: String = CA_DN,
        pathLen: Int? = null,
        serialNumber: ByteString? = null,
    ): IssuingCa {
        val key = TestKeyUtil.genEcKey(id)
        val cert = tool.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = dn
            if (serialNumber != null) serialNumberRaw = serialNumber
            extensionBasicConstraints {
                cA = true
                pathLenConstraint = pathLen
            }
        }
        return IssuingCa(key, cert)
    }

    private suspend fun issueLeaf(
        ca: IssuingCa,
        id: String = "leaf",
        dn: String = "CN=Leaf, O=Walt.id"
    ): X509Certificate {
        val leafKey = TestKeyUtil.genEcKey(id)
        return tool.createCertificate(ca.key, ca.cert, sigAlg) {
            subjectDn = dn
            subjectPublicKey(leafKey)
        }
    }

    private suspend fun issueIntermediate(
        ca: IssuingCa,
        id: String,
        dn: String = "CN=Intermediate, O=Walt.id",
    ): IssuingCa {
        val key = TestKeyUtil.genEcKey(id)
        val cert = tool.createCertificate(ca.key, ca.cert, sigAlg) {
            subjectDn = dn
            subjectPublicKey(key)
            extensionBasicConstraints { cA = true }
        }
        return IssuingCa(key, cert)
    }

    private suspend fun issueLeafUnder(intermediate: IssuingCa, id: String = "leaf-i"): X509Certificate =
        issueLeaf(intermediate, id)

    /** A self signed CA built without going through [X509CertificateUtil], so it has no Subject Key Identifier. */
    private suspend fun newCaWithoutSki(id: String, key: Key? = null): IssuingCa {
        val key = key ?: TestKeyUtil.genEcKey(id)
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = tool.services.serialNumberGenerator,
            issuerDnRaw = ByteString(),
            subjectDn = CA_DN,
        )
        builder.extensionBasicConstraints { cA = true }
        val cert = tool.services.certificateSigner.signCertificate(key, sigAlg, builder)
        return IssuingCa(key, cert)
    }

    /** A leaf built without going through [X509CertificateUtil], so it has no Authority Key Identifier. */
    private suspend fun issueLeafWithoutAki(ca: IssuingCa): X509Certificate {
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = tool.services.serialNumberGenerator,
            issuerDnRaw = ca.cert.data.subjectDnRaw,
            subjectDn = "CN=Leaf, O=Walt.id",
        )
        builder.subjectPublicKey(TestKeyUtil.genEcKey("leaf-no-aki"))
        return tool.services.certificateSigner.signCertificate(ca.key, sigAlg, builder)
    }

    @Test
    fun leafOnlyChainIsValidForEitherPinnedCa() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val trust = InMemoryTrustStore(listOf(caA.cert, caB.cert))

        for (ca in listOf(caA, caB)) {
            val result = tool.validateCertificateChain(listOf(issueLeaf(ca)), trust)
            assertTrue(result.valid, "Validation log: ${result.log}")
        }
    }

    @Test
    fun trustStoreOrderDoesNotMatter() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val leafB = issueLeaf(caB)

        assertTrue(tool.validateCertificateChain(listOf(leafB), InMemoryTrustStore(listOf(caA.cert, caB.cert))).valid)
        assertTrue(tool.validateCertificateChain(listOf(leafB), InMemoryTrustStore(listOf(caB.cert, caA.cert))).valid)
    }

    @Test
    fun fullChainThroughIntermediateIsValidForEitherPinnedCa() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val trust = InMemoryTrustStore(listOf(caA.cert, caB.cert))

        for (ca in listOf(caA, caB)) {
            val intermediate = issueIntermediate(ca, "intermediate-${ca.cert.fingerprintSha256Hex}")
            val leaf = issueLeafUnder(intermediate)
            val result = tool.validateCertificateChain(listOf(leaf, intermediate.cert), trust)
            assertTrue(result.valid, "Validation log: ${result.log}")
        }
    }

    @Test
    fun fullChainIncludingThePinnedCaItselfIsValid() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val trust = InMemoryTrustStore(listOf(caA.cert, caB.cert))

        for (ca in listOf(caA, caB)) {
            val result = tool.validateCertificateChain(listOf(issueLeaf(ca), ca.cert), trust)
            assertTrue(result.valid, "Validation log: ${result.log}")
        }
    }

    @Test
    fun chainIssuedByAnUnpinnedCaWithTheSameDnFailsClosed() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val unpinned = newCa("ca-unpinned")
        val trust = InMemoryTrustStore(listOf(caA.cert, caB.cert))

        val result = tool.validateCertificateChain(listOf(issueLeaf(unpinned)), trust)

        assertFalse(result.valid)
        assertTrue(
            result.errorLog.any {
                it.validatorId == X509CertificateSignatureValidator.ID &&
                        it.message.contains("Trusted issuer certificate") &&
                        it.message.contains("not found")
            },
            "Validation log: ${result.log}"
        )
    }

    @Test
    fun fullChainBelowAnUnpinnedCaWithTheSameDnFailsClosed() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val unpinned = newCa("ca-unpinned")
        val trust = InMemoryTrustStore(listOf(caA.cert, caB.cert))
        val intermediate = issueIntermediate(unpinned, "intermediate-unpinned")

        val result = tool.validateCertificateChain(listOf(issueLeafUnder(intermediate), intermediate.cert), trust)

        assertFalse(result.valid)
    }

    @Test
    fun selfSignedCaInChainIsMatchedByFingerprintNotByDn() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val unpinned = newCa("ca-unpinned")
        val trust = InMemoryTrustStore(listOf(caA.cert, caB.cert))

        val result = tool.validateCertificateChain(listOf(issueLeaf(unpinned), unpinned.cert), trust)

        assertFalse(result.valid)
        assertTrue(
            result.errorLog.any { it.message.contains("Trusted issuer") && it.message.contains("not found") },
            "Validation log: ${result.log}"
        )
    }

    @Test
    fun unrelatedIssuerDnIsStillReportedAsNotFound() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val other = newCa("ca-other", dn = "CN=Other CA, O=Walt.id")
        val trust = InMemoryTrustStore(listOf(caA.cert, caB.cert))

        val result = tool.validateCertificateChain(listOf(issueLeaf(other)), trust)

        assertFalse(result.valid)
        assertTrue(
            result.errorLog.any {
                it.message.contains("Trusted issuer certificate 'CN=Other CA,O=Walt.id'") &&
                        it.message.contains("not found")
            },
            "Validation log: ${result.log}"
        )
    }

    @Test
    fun sameSerialNumberOnBothCasDoesNotCollapseThem() = runTest {
        val serial = ByteString(ByteArray(8) { (it + 1).toByte() })
        val caA = newCa("ca-a", serialNumber = serial)
        val caB = newCa("ca-b", serialNumber = serial)
        assertEquals(caA.cert.data.serialNumberRaw, caB.cert.data.serialNumberRaw)

        val store = InMemoryTrustStore(listOf(caA.cert, caB.cert))
        val subjectDn = caA.cert.data.subjectDn
        assertEquals(2, store.findCertificateBySubjectDn(subjectDn).size)
        assertEquals(2, CompositeTrustStore(listOf(store)).findCertificateBySubjectDn(subjectDn).size)

        // Adding the very same certificate again does not duplicate it
        store.addCertificate(caA.cert)
        assertEquals(2, store.findCertificateBySubjectDn(subjectDn).size)

        for (ca in listOf(caA, caB)) {
            val result = tool.validateCertificateChain(listOf(issueLeaf(ca)), store)
            assertTrue(result.valid, "Validation log: ${result.log}")
        }
    }

    @Test
    fun twoCertificatesForTheSameKeyFailClosedInsteadOfPickingTheFirst() = runTest {
        val key = TestKeyUtil.genEcKey("ca-same-key")
        val first = tool.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = CA_DN
            extensionBasicConstraints { cA = true }
        }
        val second = tool.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = CA_DN
            extensionBasicConstraints { cA = true }
        }
        assertTrue(first.fingerprintSha256 != second.fingerprintSha256)
        val ca = IssuingCa(key, first)
        val trust = InMemoryTrustStore(listOf(first, second))

        val result = tool.validateCertificateChain(listOf(issueLeaf(ca)), trust)

        assertFalse(result.valid)
        assertTrue(
            result.errorLog.any { it.message.contains("Refusing to select one") },
            "Validation log: ${result.log}"
        )
    }

    @Test
    fun theSelectedIssuersSignatureIsVerifiedOnlyOncePerCandidate() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        val delegate = tool.services.signatureValidator
        var verifications = 0
        val counting = object : SignatureValidator by delegate {
            override suspend fun validateCertificateSignature(
                cryptoRuntime: CryptoRuntime,
                issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
                certificate: X509Certificate
            ): Boolean {
                verifications++
                return delegate.validateCertificateSignature(cryptoRuntime, issuerPublicKey, certificate)
            }
        }
        val validator = X509CertificateChainValidator(
            listOf(X509CertificateSignatureValidator(counting)),
            InMemoryTrustStore(listOf(caA.cert, caB.cert))
        )

        val result = validator.validate(TestKeyUtil.runtime, listOf(issueLeaf(caB)))

        assertTrue(result.valid, "Validation log: ${result.log}")
        // The leaf's AKI selects caB as the preferred candidate, which verifies: one verification in total
        assertEquals(1, verifications)
    }

    @Test
    fun pathLengthWalkFailsClosedOnAmbiguousIssuerWithoutASignatureValidator() = runTest {
        val caA = newCa("ca-a")
        val caB = newCa("ca-b")
        // The intermediate has no Authority Key Identifier, and without a signature validator nothing else
        // can tell caA and caB apart; nothing else reports the ambiguity either.
        val intermediateKey = TestKeyUtil.genEcKey("intermediate-no-aki")
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = tool.services.serialNumberGenerator,
            issuerDnRaw = caB.cert.data.subjectDnRaw,
            subjectDn = "CN=Intermediate, O=Walt.id",
        )
        builder.subjectPublicKey(intermediateKey)
        builder.extensionBasicConstraints { cA = true }
        val intermediate = IssuingCa(
            intermediateKey,
            tool.services.certificateSigner.signCertificate(caB.key, sigAlg, builder)
        )
        assertEquals(null, intermediate.cert.data.extensionAuthorityKeyIdentifier)
        val leaf = issueLeafUnder(intermediate)
        val validator = X509CertificateChainValidator(
            listOf(X509CertificateBasicConstraintsValidator()),
            InMemoryTrustStore(listOf(caA.cert, caB.cert))
        )

        val result = validator.validate(TestKeyUtil.runtime, listOf(leaf, intermediate.cert))

        assertFalse(result.valid, "Validation log: ${result.log}")
        assertTrue(
            result.errorLog.any {
                it.validatorId == X509CertificateBasicConstraintsValidator.ID &&
                        it.message.contains("Cannot determine path length constraints")
            },
            "Validation log: ${result.log}"
        )
    }

    @Test
    fun rolloverBetweenKeyTypesSelectsTheCaByItsKey() = runTest {
        // An RSA CA rolled over to an EC CA (or the other way round) with the same subject DN: verifying the
        // signature against the candidate of the other key type must count as "does not verify", not throw.
        val rsaSigAlg = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
        val rsaKey = TestKeyUtil.genRsaKey("ca-rsa")
        val rsaCert = tool.createSelfSignedCertificate(rsaKey, rsaSigAlg) {
            subjectDn = CA_DN
            extensionBasicConstraints { cA = true }
        }
        val rsaCa = IssuingCa(rsaKey, rsaCert)
        val ecCa = newCa("ca-ec")
        val trust = InMemoryTrustStore(listOf(rsaCa.cert, ecCa.cert))

        val rsaLeafKey = TestKeyUtil.genEcKey("leaf-of-rsa-ca")
        val leafOfRsaCa = tool.createCertificate(rsaCa.key, rsaCa.cert, rsaSigAlg) {
            subjectDn = "CN=Leaf, O=Walt.id"
            subjectPublicKey(rsaLeafKey)
        }

        assertTrue(tool.validateCertificateChain(listOf(leafOfRsaCa), trust).valid)
        assertTrue(tool.validateCertificateChain(listOf(issueLeaf(ecCa)), trust).valid)
    }

    @Test
    fun pathLengthConstraintOfTheSelectedIssuerIsUsed() = runTest {
        // Same DN: caA allows no intermediates (pathLen 0), caB allows one (pathLen 1).
        val caA = newCa("ca-a", pathLen = 0)
        val caB = newCa("ca-b", pathLen = 1)
        val trust = InMemoryTrustStore(listOf(caA.cert, caB.cert))

        // Through caB the intermediate is permitted ...
        val viaB = issueIntermediate(caB, "intermediate-b")
        val validResult = tool.validateCertificateChain(listOf(issueLeafUnder(viaB, "leaf-b"), viaB.cert), trust)
        assertTrue(validResult.valid, "Validation log: ${validResult.log}")

        // ... through caA it exceeds pathLen 0, even though caB (pathLen 1) shares its DN.
        val viaA = issueIntermediate(caA, "intermediate-a")
        val invalidResult =
            tool.validateCertificateChain(listOf(issueLeafUnder(viaA, "leaf-a"), viaA.cert), trust)
        assertFalse(invalidResult.valid)
        assertTrue(
            invalidResult.errorLog.any {
                it.validatorId == X509CertificateBasicConstraintsValidator.ID && it.message.contains("path length")
            },
            "Validation log: ${invalidResult.log}"
        )
    }

    companion object {
        private const val CA_DN = "CN=Rollover CA, O=Walt.id"
        private val tool = X509CertificateUtil
    }
}
