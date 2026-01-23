package id.walt.webwallet.utils

import id.walt.webwallet.service.account.x5c.X5CValidator
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPairGenerator
import java.security.Security
import java.util.*
import kotlin.test.Test
import kotlin.test.assertTrue


class X5CValidatorTest {

    //we don't care about the bit size of the key, it's a test case (as long as it's bigger than 1024)
    private val keyPairGenerator = KeyPairGenerator
        .getInstance("RSA").apply {
            initialize(2048)
        }

    //x.509 certificate expiration dates
    private val nonExpiredValidFrom = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
    private val nonExpiredValidTo = Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000L)
    private val expiredValidFrom = Date(System.currentTimeMillis() - 48 * 60 * 60 * 1000)
    private val expiredValidTo = nonExpiredValidFrom

    private val rootCAKeyPair = keyPairGenerator.generateKeyPair()
    private val rootCADistinguishedName = X500Name("CN=SomeRoot")
    private val nonExpiredRootCACertificate = PKIXUtils.generateRootCACertificate(
        rootCAKeyPair,
        nonExpiredValidFrom,
        nonExpiredValidTo,
        rootCADistinguishedName,
    )
    private val pemEncodedNonExpiredRootCACertificate = PKIXUtils.exportX509CertificateToPEM(nonExpiredRootCACertificate)
    private val base64NonExpiredRootCACertificate =
        Base64.getEncoder().encodeToString(nonExpiredRootCACertificate.encoded)
    private val expiredRootCACertificate = PKIXUtils.generateRootCACertificate(
        rootCAKeyPair,
        expiredValidFrom,
        expiredValidTo,
        rootCADistinguishedName,
    )
    private val pemEncodedExpiredRootCACertificate = PKIXUtils.exportX509CertificateToPEM(expiredRootCACertificate)
    private val base64ExpiredRootCACertificate = Base64.getEncoder().encodeToString(expiredRootCACertificate.encoded)

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun singlex5cEntryValidNoTrustedCA() = runTest {
        val subjectKeyPair = keyPairGenerator.generateKeyPair()
        val subjectDistinguishedName = X500Name("CN=SomeSubject")
        val subjectCert = PKIXUtils.generateSubjectCertificate(
            rootCAKeyPair.private,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            subjectDistinguishedName,
        )
        val base64EncodedSubjectCert = Base64.getEncoder().encodeToString(subjectCert.encoded)
        val x5cValidator = X5CValidator(emptyList())
        val validationResult = x5cValidator.validate(listOf(base64EncodedSubjectCert))
        assertTrue { validationResult.isFailure }
    }

    @Test
    fun singlex5cEntryValidAndSingleTrustedCAValid() = runTest {
        val subjectKeyPair = keyPairGenerator.generateKeyPair()
        val subjectDistinguishedName = X500Name("CN=SomeSubject")
        val subjectCert = PKIXUtils.generateSubjectCertificate(
            rootCAKeyPair.private,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            subjectDistinguishedName,
        )
        val base64EncodedSubjectCert = Base64.getEncoder().encodeToString(subjectCert.encoded)
        val x5cValidator = X5CValidator(listOf(pemEncodedNonExpiredRootCACertificate))
        val validationResult = x5cValidator.validate(listOf(base64EncodedSubjectCert))
        assertTrue { validationResult.isSuccess }
    }

    @Test
    fun singlex5cEntryValidAndSingleTrustedCAExpired() = runTest {
        val subjectKeyPair = keyPairGenerator.generateKeyPair()
        val subjectDistinguishedName = X500Name("CN=SomeSubject")
        val subjectCert = PKIXUtils.generateSubjectCertificate(
            rootCAKeyPair.private,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            subjectDistinguishedName,
        )
        val base64EncodedSubjectCert = Base64.getEncoder().encodeToString(subjectCert.encoded)
        val x5cValidator = X5CValidator(listOf(pemEncodedExpiredRootCACertificate))
        val validationResult = x5cValidator.validate(listOf(base64EncodedSubjectCert))
        assertTrue { validationResult.isSuccess }
    }

    @Test
    fun singlex5cEntryExpiredAndSingleTrustedCAExpired() = runTest {
        val subjectKeyPair = keyPairGenerator.generateKeyPair()
        val subjectDistinguishedName = X500Name("CN=SomeSubject")
        val subjectCert = PKIXUtils.generateSubjectCertificate(
            rootCAKeyPair.private,
            subjectKeyPair.public,
            expiredValidFrom,
            expiredValidTo,
            rootCADistinguishedName,
            subjectDistinguishedName,
        )
        val base64EncodedSubjectCert = Base64.getEncoder().encodeToString(subjectCert.encoded)
        val x5cValidator = X5CValidator(listOf(pemEncodedExpiredRootCACertificate))
        val validationResult = x5cValidator.validate(listOf(base64EncodedSubjectCert))
        assertTrue { validationResult.isFailure }
    }

    @Test
    fun subjectValidIntermediateCAValidx5cEntriesAndSingleRootCAValid() = runTest {
        val intermediateCAKeyPair = keyPairGenerator.generateKeyPair()
        val intermediateCADistinguishedName = X500Name("CN=SomeIntermediate")
        val subjectKeyPair = keyPairGenerator.generateKeyPair()
        val subjectDistinguishedName = X500Name("CN=SomeSubject")
        val intermediateCACert = PKIXUtils.generateIntermediateCACertificate(
            rootCAKeyPair.private,
            intermediateCAKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            intermediateCADistinguishedName,
        )
        val subjectCert = PKIXUtils.generateSubjectCertificate(
            intermediateCAKeyPair.private,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            intermediateCADistinguishedName,
            subjectDistinguishedName,
        )
        val base64EncodedSubjectCert = Base64.getEncoder().encodeToString(subjectCert.encoded)
        val base64EncodedIntermediateCert = Base64.getEncoder().encodeToString(intermediateCACert.encoded)
        val x5cValidator = X5CValidator(
            listOf(
                pemEncodedNonExpiredRootCACertificate,
            )
        )
        val fullPathValidationResult = x5cValidator.validate(
            listOf(
                base64EncodedSubjectCert,
                base64EncodedIntermediateCert,
                base64NonExpiredRootCACertificate,
            )
        )
        assertTrue { fullPathValidationResult.isSuccess }
        val partialPathValidationResult = x5cValidator.validate(
            listOf(
                base64EncodedSubjectCert,
                base64EncodedIntermediateCert,
            )
        )
        assertTrue { partialPathValidationResult.isSuccess }
    }

    @Test
    fun subjectValidIntermediateCAExpiredx5cEntriesAndSingleRootCAValid() = runTest {
        val intermediateCAKeyPair = keyPairGenerator.generateKeyPair()
        val intermediateCADistinguishedName = X500Name("CN=SomeIntermediate")
        val subjectKeyPair = keyPairGenerator.generateKeyPair()
        val subjectDistinguishedName = X500Name("CN=SomeSubject")
        val intermediateCACert = PKIXUtils.generateIntermediateCACertificate(
            rootCAKeyPair.private,
            intermediateCAKeyPair.public,
            expiredValidFrom,
            expiredValidTo,
            rootCADistinguishedName,
            intermediateCADistinguishedName,
        )
        val subjectCert = PKIXUtils.generateSubjectCertificate(
            intermediateCAKeyPair.private,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            intermediateCADistinguishedName,
            subjectDistinguishedName,
        )
        val base64EncodedSubjectCert = Base64.getEncoder().encodeToString(subjectCert.encoded)
        val base64EncodedIntermediateCert = Base64.getEncoder().encodeToString(intermediateCACert.encoded)
        val x5cValidator = X5CValidator(
            listOf(
                pemEncodedNonExpiredRootCACertificate,
            )
        )
        val fullPathValidationResult = x5cValidator.validate(
            listOf(
                base64EncodedSubjectCert,
                base64EncodedIntermediateCert,
                base64NonExpiredRootCACertificate,
            )
        )
        assertTrue { fullPathValidationResult.isFailure }
        val partialPathValidationResult = x5cValidator.validate(
            listOf(
                base64EncodedSubjectCert,
                base64EncodedIntermediateCert,
            )
        )
        assertTrue { partialPathValidationResult.isFailure }
    }

    @Test
    fun subjectValidIntermediateCAValidx5cEntriesAndSingleRootCAExpired() = runTest {
        val intermediateCAKeyPair = keyPairGenerator.generateKeyPair()
        val intermediateCADistinguishedName = X500Name("CN=SomeIntermediate")
        val subjectKeyPair = keyPairGenerator.generateKeyPair()
        val subjectDistinguishedName = X500Name("CN=SomeSubject")
        val intermediateCACert = PKIXUtils.generateIntermediateCACertificate(
            rootCAKeyPair.private,
            intermediateCAKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            intermediateCADistinguishedName,
        )
        val subjectCert = PKIXUtils.generateSubjectCertificate(
            intermediateCAKeyPair.private,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            intermediateCADistinguishedName,
            subjectDistinguishedName,
        )
        val base64EncodedSubjectCert = Base64.getEncoder().encodeToString(subjectCert.encoded)
        val base64EncodedIntermediateCert = Base64.getEncoder().encodeToString(intermediateCACert.encoded)
        val x5cValidator = X5CValidator(
            listOf(
                pemEncodedExpiredRootCACertificate,
            )
        )
        val fullPathValidationResult = x5cValidator.validate(
            listOf(
                base64EncodedSubjectCert,
                base64EncodedIntermediateCert,
                base64ExpiredRootCACertificate,
            )
        )
        assertTrue { fullPathValidationResult.isFailure }
        val partialPathValidationResult = x5cValidator.validate(
            listOf(
                base64EncodedSubjectCert,
                base64EncodedIntermediateCert,
            )
        )
        assertTrue { partialPathValidationResult.isSuccess }
    }
}
