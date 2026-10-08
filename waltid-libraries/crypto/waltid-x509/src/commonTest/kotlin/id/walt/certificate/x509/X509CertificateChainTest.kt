package id.walt.certificate.x509

import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.validation.X509CertificateChain
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509CertificateChainTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    private class Issued(val key: Key, val certificate: X509Certificate)

    private suspend fun root(name: String): Issued {
        val key = TestKeyUtil.genEcKey(name)
        return Issued(key, X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "cn=$name, o=Walt.id"
            extensionBasicConstraints { cA = true }
        })
    }

    private suspend fun Issued.issue(name: String, keyId: String = name, subjectKey: Key? = null): Issued {
        val key = subjectKey ?: TestKeyUtil.genEcKey(keyId)
        return Issued(key, X509CertificateUtil.createCertificate(this.key, certificate, sigAlg) {
            subjectDn = "cn=$name, o=Walt.id"
            subjectPublicKey(key)
            extensionBasicConstraints { cA = true }
            extensionSubjectKeyIdentifier()
        })
    }

    @Test
    fun ordersLeafFirstChainRootFirstAndRemembersProvidedPositions() = runTest {
        val root = root("Root")
        val intermediate = root.issue("Intermediate")
        val leaf = intermediate.issue("Leaf")

        val chain = X509CertificateChain.of(
            listOf(leaf.certificate, intermediate.certificate, root.certificate)
        )

        assertEquals(listOf(root.certificate, intermediate.certificate, leaf.certificate), chain.certificates)
        assertEquals(listOf(2, 1, 0), (0..<chain.size).map { chain.getEntry(it).indexInProvidedChain })
        assertTrue(chain.isProvidedLeafFirst)
    }

    @Test
    fun ordersShuffledChainButReportsItIsNotLeafFirst() = runTest {
        val root = root("Root")
        val intermediate = root.issue("Intermediate")
        val leaf = intermediate.issue("Leaf")

        val chain = X509CertificateChain.of(
            listOf(intermediate.certificate, leaf.certificate, root.certificate)
        )

        assertEquals(listOf(root.certificate, intermediate.certificate, leaf.certificate), chain.certificates)
        assertEquals(listOf(2, 0, 1), (0..<chain.size).map { chain.getEntry(it).indexInProvidedChain })
        assertFalse(chain.isProvidedLeafFirst)
    }

    @Test
    fun chainWithoutRootStartsAtTopmostProvidedCertificate() = runTest {
        val root = root("Root")
        val intermediate = root.issue("Intermediate")
        val leaf = intermediate.issue("Leaf")

        val chain = X509CertificateChain.of(listOf(leaf.certificate, intermediate.certificate))

        assertEquals(listOf(intermediate.certificate, leaf.certificate), chain.certificates)
        assertTrue(chain.isProvidedLeafFirst)
    }

    @Test
    fun ignoresExactDuplicatesButReportsThemAsNotLeafFirst() = runTest {
        val root = root("Root")
        val leaf = root.issue("Leaf")

        val chain = X509CertificateChain.of(listOf(leaf.certificate, leaf.certificate, root.certificate))

        assertEquals(listOf(root.certificate, leaf.certificate), chain.certificates)
        assertEquals(listOf(2, 0), (0..<chain.size).map { chain.getEntry(it).indexInProvidedChain })
        assertFalse(chain.isProvidedLeafFirst)
    }

    @Test
    fun rejectsEmptyChain() {
        val failure = assertFailsWith<IllegalArgumentException> { X509CertificateChain.of(emptyList()) }
        assertContains(failure.message.orEmpty(), "empty")
    }

    @Test
    fun rejectsUnrelatedCertificates() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            X509CertificateChain.of(listOf(root("Root A").certificate, root("Root B").certificate))
        }
        assertContains(failure.message.orEmpty(), "multiple roots")
    }

    @Test
    fun rejectsBranchingChain() = runTest {
        val root = root("Root")
        val failure = assertFailsWith<IllegalArgumentException> {
            X509CertificateChain.of(
                listOf(root.issue("Leaf A").certificate, root.issue("Leaf B").certificate, root.certificate)
            )
        }
        assertContains(failure.message.orEmpty(), "branches")
    }

    @Test
    fun rejectsIssuerCycleWithoutRoot() = runTest {
        // A2 issues B, and a second certificate with A2's subject DN and key is issued by B: every issuer
        // (by DN and key identifier) is in the list.
        val a = root("A").issue("A2")
        val b = a.issue("B")
        val aIssuedByB = b.issue("A2", subjectKey = a.key)

        val failure = assertFailsWith<IllegalArgumentException> {
            X509CertificateChain.of(listOf(b.certificate, aIssuedByB.certificate))
        }
        assertContains(failure.message.orEmpty(), "cycle")
    }

    @Test
    fun terminatesWhenSubjectDnRepeatsInsideTheChain() = runTest {
        // Root -> A -> B -> A' where A' shares A's subject DN: walking by issuer DN would revisit B forever.
        val root = root("Root")
        val a = root.issue("A")
        val b = a.issue("B")
        val aPrime = b.issue("A")

        val chain = X509CertificateChain.of(
            listOf(aPrime.certificate, b.certificate, a.certificate, root.certificate)
        )

        assertEquals(
            listOf(root.certificate, a.certificate, b.certificate, aPrime.certificate),
            chain.certificates,
        )
        assertTrue(chain.isProvidedLeafFirst)
    }

    @Test
    fun usesAuthorityKeyIdToPickTheIssuerAmongCertificatesWithTheSameSubjectDn() = runTest {
        // Two CA certificates share the subject DN but have different keys; only the AKI tells them apart.
        val root = root("Root")
        val caOld = root.issue("CA", keyId = "CA old")
        val caNew = root.issue("CA", keyId = "CA new")
        val leaf = caNew.issue("Leaf")

        val failure = assertFailsWith<IllegalArgumentException> {
            X509CertificateChain.of(listOf(leaf.certificate, caOld.certificate, caNew.certificate, root.certificate))
        }
        assertContains(failure.message.orEmpty(), "branches")

        val chain = X509CertificateChain.of(listOf(leaf.certificate, caNew.certificate, root.certificate))
        assertEquals(listOf(root.certificate, caNew.certificate, leaf.certificate), chain.certificates)
        assertTrue(chain.isProvidedLeafFirst)
    }

    @Test
    fun rejectsIssuerWhoseSubjectKeyIdDoesNotMatchTheAuthorityKeyId() = runTest {
        val root = root("Root")
        val caNew = root.issue("CA", keyId = "CA new")
        val caOld = root.issue("CA", keyId = "CA old")
        val leaf = caNew.issue("Leaf")

        // The DNs link, but the leaf's AKI names a different key than the provided CA's SKI.
        val failure = assertFailsWith<IllegalArgumentException> {
            X509CertificateChain.of(listOf(leaf.certificate, caOld.certificate))
        }
        assertContains(failure.message.orEmpty(), "multiple roots")
    }
}
