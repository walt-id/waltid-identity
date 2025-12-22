package id.walt.webwallet.config

import id.walt.webwallet.utils.PKIXUtils
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import java.security.KeyPairGenerator
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFails

class TrustedCAConfigTest {

    //we don't care about the bit size of the key, it's a test case (as long as it's bigger than 512)
    private val keyPairGenerator = KeyPairGenerator
        .getInstance("RSA").apply {
            initialize(1024)
        }

    //x.509 certificate expiration dates
    private val nonExpiredValidFrom = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
    private val nonExpiredValidTo = Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000L)
    private val expiredValidFrom = Date(System.currentTimeMillis() - 48 * 60 * 60 * 1000)
    private val expiredValidTo = nonExpiredValidFrom

    private val rootCAKeyPair = keyPairGenerator.generateKeyPair()
    private val rootCADistinguishedName = X500Name("CN=SomeRoot")

    @Test
    fun checkValidCACertificate() = runTest {
        val caCert = PKIXUtils.generateRootCACertificate(
            rootCAKeyPair,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
        )
        val pemEncodedCACert = PKIXUtils.exportX509CertificateToPEM(caCert)
        //assertion is implicit here as no exception must be thrown
        TrustedCAConfig(listOf(pemEncodedCACert))
    }

    @Test
    fun checkMultipleValidCACertificates() = runTest {
        val caCertList = listOf(
            PKIXUtils.generateRootCACertificate(
                keyPairGenerator.generateKeyPair(),
                nonExpiredValidFrom,
                nonExpiredValidTo,
                X500Name("CN=SomeRoot1"),
            ),
            PKIXUtils.generateRootCACertificate(
                keyPairGenerator.generateKeyPair(),
                nonExpiredValidFrom,
                nonExpiredValidTo,
                X500Name("CN=SomeRoot2"),
            ),
            PKIXUtils.generateRootCACertificate(
                keyPairGenerator.generateKeyPair(),
                nonExpiredValidFrom,
                nonExpiredValidTo,
                X500Name("CN=SomeRoot3"),
            ),
        )
        //assertion is implicit here as no exception must be thrown
        TrustedCAConfig(caCertList.map {
            PKIXUtils.exportX509CertificateToPEM(it)
        })
    }

    @Test
    fun checkExpiredCACertificateThrows() = runTest {
        val caCert = PKIXUtils.generateRootCACertificate(
            rootCAKeyPair,
            expiredValidFrom,
            expiredValidTo,
            rootCADistinguishedName,
        )
        val pemEncodedCACert = PKIXUtils.exportX509CertificateToPEM(caCert)
        assertFails { TrustedCAConfig(listOf(pemEncodedCACert)) }
    }

    @Test
    fun checkNonCACertificateThrows() = runTest {
        val caCert = PKIXUtils.generateSubjectCertificate(
            rootCAKeyPair.private,
            keyPairGenerator.generateKeyPair().public,
            expiredValidFrom,
            expiredValidTo,
            rootCADistinguishedName,
            X500Name("CN=SomeName"),
        )
        val pemEncodedCACert = PKIXUtils.exportX509CertificateToPEM(caCert)
        assertFails { TrustedCAConfig(listOf(pemEncodedCACert)) }
    }

    @Test
    fun checkMultipleCACertificatesOneExpiredThrows() = runTest {
        val caCertList = listOf(
            PKIXUtils.generateRootCACertificate(
                keyPairGenerator.generateKeyPair(),
                nonExpiredValidFrom,
                nonExpiredValidTo,
                X500Name("CN=SomeRoot1"),
            ),
            PKIXUtils.generateRootCACertificate(
                keyPairGenerator.generateKeyPair(),
                expiredValidFrom,
                expiredValidTo,
                X500Name("CN=SomeRoot2"),
            ),
            PKIXUtils.generateRootCACertificate(
                keyPairGenerator.generateKeyPair(),
                nonExpiredValidFrom,
                nonExpiredValidTo,
                X500Name("CN=SomeRoot3"),
            ),
        )
        assertFails {
            TrustedCAConfig(caCertList.map {
                PKIXUtils.exportX509CertificateToPEM(it)
            })
        }
    }

    @Test
    fun checkMultipleCertificatesOneNonCAThrows() = runTest {
        val caCertList = listOf(
            PKIXUtils.generateRootCACertificate(
                keyPairGenerator.generateKeyPair(),
                nonExpiredValidFrom,
                nonExpiredValidTo,
                X500Name("CN=SomeRoot1"),
            ),
            PKIXUtils.generateSubjectCertificate(
                rootCAKeyPair.private,
                keyPairGenerator.generateKeyPair().public,
                expiredValidFrom,
                expiredValidTo,
                rootCADistinguishedName,
                X500Name("CN=SomeName"),
            ),
            PKIXUtils.generateRootCACertificate(
                keyPairGenerator.generateKeyPair(),
                nonExpiredValidFrom,
                nonExpiredValidTo,
                X500Name("CN=SomeRoot3"),
            ),
        )
        assertFails {
            TrustedCAConfig(caCertList.map {
                PKIXUtils.exportX509CertificateToPEM(it)
            })
        }
    }
}
