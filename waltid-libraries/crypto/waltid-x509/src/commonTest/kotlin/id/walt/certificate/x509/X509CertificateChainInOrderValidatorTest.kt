package id.walt.certificate.x509

import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.validator.X509CertificateChainInOrderValidator
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class X509CertificateChainInOrderValidatorTest {

    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
    private val util = X509CertificateUtil { addValidators(X509CertificateChainInOrderValidator()) }

    private class Issued(val key: Key, val certificate: X509Certificate)

    private class Chain(val root: X509Certificate, val intermediate: X509Certificate, val leaf: X509Certificate)

    private suspend fun chain(): Chain {
        val rootKey = TestKeyUtil.genEcKey("root")
        val root = Issued(rootKey, util.createSelfSignedCertificate(rootKey, sigAlg) {
            subjectDn = "cn=Root, o=Walt.id"
            extensionBasicConstraints { cA = true }
        })
        val intermediate = root.issue("Intermediate")
        return Chain(root.certificate, intermediate.certificate, intermediate.issue("Leaf").certificate)
    }

    private suspend fun Issued.issue(name: String): Issued {
        val key = TestKeyUtil.genEcKey(name)
        return Issued(key, util.createCertificate(this.key, certificate, sigAlg) {
            subjectDn = "cn=$name, o=Walt.id"
            subjectPublicKey(key)
            extensionBasicConstraints { cA = true }
        })
    }

    // Default validators stay active, so only the entries of the validator under test are evaluated.
    private suspend fun orderErrors(certificates: List<X509Certificate>, trust: X509Certificate) =
        util.validateCertificateChain(certificates, InMemoryTrustStore(listOf(trust))).log.filter {
            it.severity == ValidationResult.Severity.ERROR && it.validatorId == X509CertificateChainInOrderValidator.ID
        }

    @Test
    fun acceptsLeafFirstChain() = runTest {
        val chain = chain()

        assertEquals(
            emptyList(),
            orderErrors(listOf(chain.leaf, chain.intermediate, chain.root), chain.root),
        )
    }

    @Test
    fun acceptsLeafFirstChainWithoutRoot() = runTest {
        val chain = chain()

        assertEquals(emptyList(), orderErrors(listOf(chain.leaf, chain.intermediate), chain.root))
    }

    @Test
    fun acceptsSingleCertificate() = runTest {
        val chain = chain()

        assertEquals(emptyList(), orderErrors(listOf(chain.leaf), chain.intermediate))
    }

    @Test
    fun rejectsRootFirstChain() = runTest {
        val chain = chain()

        val errors = orderErrors(listOf(chain.root, chain.intermediate, chain.leaf), chain.root)

        // The intermediate is in the middle either way; root and leaf are swapped.
        assertEquals(
            setOf(chain.root.data.subjectDn, chain.leaf.data.subjectDn),
            errors.map { it.subjectDn }.toSet(),
        )
    }

    @Test
    fun rejectsShuffledChain() = runTest {
        val chain = chain()

        val errors = orderErrors(listOf(chain.intermediate, chain.leaf, chain.root), chain.root)

        assertEquals(2, errors.size, "Log: $errors")
        assertTrue(errors.all { it.message.contains("leaf first") }, "Log: $errors")
    }
}
