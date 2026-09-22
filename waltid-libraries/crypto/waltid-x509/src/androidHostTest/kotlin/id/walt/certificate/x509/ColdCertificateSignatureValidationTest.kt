package id.walt.certificate.x509

import id.walt.certificate.TestData
import id.walt.certificate.x509.revocation.CrlTestFixtures
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import java.security.Security
import java.net.URLClassLoader
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColdCertificateSignatureValidationTest {
    @Test
    fun certificateAndCsrValidationDoNotRequireGlobalBouncyCastleRegistration() {
        val classpath = generateSequence(javaClass.classLoader) { it.parent }
            .filterIsInstance<URLClassLoader>().flatMap { it.urLs.asSequence() }
            .map { File(it.toURI()).path }.toList() + System.getProperty("java.class.path").split(File.pathSeparator)
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").path,
            "-cp", classpath.distinct().joinToString(File.pathSeparator), javaClass.name,
        ).redirectErrorStream(true).start()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Cold validation subprocess timed out")
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), output)
            assertTrue(output.contains("cold-certificate-validation-passed"), output)
        } finally { process.destroyForcibly() }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = runTest {
            val services = X509CertificateUtil.services
            val issuer = X509CertificateUtil.parseCertificateDerEncoded(ByteString(CrlTestFixtures.der("EC256_CA")))
            val certificate = X509CertificateUtil.parseCertificateDerEncoded(ByteString(CrlTestFixtures.der("EC256_LEAF")))
            val csr = X509CertificateUtil.parseCsrPem(TestData.csrPem)
            // This process owns its provider registry; the test worker and parallel tests are untouched.
            Security.removeProvider("BC")
            assertTrue(services.signatureValidator.validateCertificateSignature(services.cryptoRuntime, issuer.data.subjectPublicKeyInfo, certificate))
            assertTrue(services.signatureValidator.validateCsrSignature(services.cryptoRuntime, csr))
            assertNull(Security.getProvider("BC"))
            println("cold-certificate-validation-passed")
        }
    }
}
