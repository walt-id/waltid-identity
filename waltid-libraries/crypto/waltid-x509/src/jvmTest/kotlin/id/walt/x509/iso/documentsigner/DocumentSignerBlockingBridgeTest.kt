package id.walt.x509.iso.documentsigner

import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentSignerBlockingBridgeTest {

    @Test
    fun `blocking and suspend build parse validate are equivalent when run sequentially`() = runTest {

        val iacaProfileData = IsoSharedTestHarnessValidResources.iacaProfileData
        val iacaSigningKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey()

        val iacaBundle = IsoSharedTestHarnessValidResources.iacaBuilder.build(
            profileData = iacaProfileData,
            signingKey = iacaSigningKey,
        )
        val iacaSignerSpec = IACASignerSpecification(
            profileData = iacaProfileData,
            signingKey = iacaSigningKey,
        )

        val dsProfileData = IsoSharedTestHarnessValidResources.dsProfileData
        val dsPublicKey = IsoSharedTestHarnessValidResources.dsSecp256r1PublicKey()

        val blockingBundle = IsoSharedTestHarnessValidResources.dsBuilder.buildBlocking(
            profileData = dsProfileData,
            publicKey = dsPublicKey,
            iacaSignerSpec = iacaSignerSpec,
        )
        val suspendBundle = IsoSharedTestHarnessValidResources.dsBuilder.build(
            profileData = dsProfileData,
            publicKey = dsPublicKey,
            iacaSignerSpec = iacaSignerSpec,
        )

        assertEquals(
            expected = blockingBundle.decodedCertificate.toDocumentSignerCertificateProfileData(),
            actual = suspendBundle.decodedCertificate.toDocumentSignerCertificateProfileData(),
        )
        assertEquals(
            expected = blockingBundle.decodedCertificate.issuerPrincipalName,
            actual = suspendBundle.decodedCertificate.issuerPrincipalName,
        )

        val parsedBlocking = IsoSharedTestHarnessValidResources.dsParser.parseBlocking(
            certificate = blockingBundle.certificateDer,
        )
        val parsedSuspend = IsoSharedTestHarnessValidResources.dsParser.parse(
            certificate = blockingBundle.certificateDer,
        )

        assertEquals(
            expected = parsedBlocking,
            actual = parsedSuspend,
        )

        IsoSharedTestHarnessValidResources.dsValidator.validateBlocking(
            dsDecodedCert = parsedBlocking,
            iacaDecodedCert = iacaBundle.decodedCertificate,
        )
        IsoSharedTestHarnessValidResources.dsValidator.validate(
            dsDecodedCert = parsedSuspend,
            iacaDecodedCert = iacaBundle.decodedCertificate,
        )

    }

    @Test
    fun `blocking and suspend build parse validate are equivalent when run concurrently`() = runTest {

        val iacaProfileData = IsoSharedTestHarnessValidResources.iacaProfileData
        val iacaSigningKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey()

        val iacaBundle = IsoSharedTestHarnessValidResources.iacaBuilder.build(
            profileData = iacaProfileData,
            signingKey = iacaSigningKey,
        )
        val iacaSignerSpec = IACASignerSpecification(
            profileData = iacaProfileData,
            signingKey = iacaSigningKey,
        )

        val dsProfileData = IsoSharedTestHarnessValidResources.dsProfileData
        val dsPublicKey = IsoSharedTestHarnessValidResources.dsSecp256r1PublicKey()

        val blockingBuild = async(Dispatchers.Default) {
            IsoSharedTestHarnessValidResources.dsBuilder.buildBlocking(
                profileData = dsProfileData,
                publicKey = dsPublicKey,
                iacaSignerSpec = iacaSignerSpec,
            )
        }
        val suspendBuild = async(Dispatchers.Default) {
            IsoSharedTestHarnessValidResources.dsBuilder.build(
                profileData = dsProfileData,
                publicKey = dsPublicKey,
                iacaSignerSpec = iacaSignerSpec,
            )
        }

        val blockingBundle = blockingBuild.await()
        val suspendBundle = suspendBuild.await()

        assertEquals(
            expected = blockingBundle.decodedCertificate.toDocumentSignerCertificateProfileData(),
            actual = suspendBundle.decodedCertificate.toDocumentSignerCertificateProfileData(),
        )
        assertEquals(
            expected = blockingBundle.decodedCertificate.issuerPrincipalName,
            actual = suspendBundle.decodedCertificate.issuerPrincipalName,
        )

        val parsedBlockingDeferred = async(Dispatchers.Default) {
            IsoSharedTestHarnessValidResources.dsParser.parseBlocking(
                certificate = blockingBundle.certificateDer,
            )
        }
        val parsedSuspendDeferred = async(Dispatchers.Default) {
            IsoSharedTestHarnessValidResources.dsParser.parse(
                certificate = blockingBundle.certificateDer,
            )
        }

        val parsedBlocking = parsedBlockingDeferred.await()
        val parsedSuspend = parsedSuspendDeferred.await()

        assertEquals(
            expected = parsedBlocking,
            actual = parsedSuspend,
        )

        awaitAll(
            async(Dispatchers.Default) {
                IsoSharedTestHarnessValidResources.dsValidator.validateBlocking(
                    dsDecodedCert = parsedBlocking,
                    iacaDecodedCert = iacaBundle.decodedCertificate,
                )
            },
            async(Dispatchers.Default) {
                IsoSharedTestHarnessValidResources.dsValidator.validate(
                    dsDecodedCert = parsedSuspend,
                    iacaDecodedCert = iacaBundle.decodedCertificate,
                )
            },
        )
    }

    @Test
    fun `blocking and suspend apis handle consistently multiple concurrent invocations`() = runTest {

        val iacaProfileData = IsoSharedTestHarnessValidResources.iacaProfileData
        val iacaSigningKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey()

        val iacaBundle = IsoSharedTestHarnessValidResources.iacaBuilder.build(
            profileData = iacaProfileData,
            signingKey = iacaSigningKey,
        )
        val iacaSignerSpec = IACASignerSpecification(
            profileData = iacaProfileData,
            signingKey = iacaSigningKey,
        )

        val dsProfileData = IsoSharedTestHarnessValidResources.dsProfileData
        val dsPublicKey = IsoSharedTestHarnessValidResources.dsSecp256r1PublicKey()

        List(12) {
            async(Dispatchers.Default) {
                val blockingBundle = IsoSharedTestHarnessValidResources.dsBuilder.buildBlocking(
                    profileData = dsProfileData,
                    publicKey = dsPublicKey,
                    iacaSignerSpec = iacaSignerSpec,
                )
                val suspendBundle = IsoSharedTestHarnessValidResources.dsBuilder.build(
                    profileData = dsProfileData,
                    publicKey = dsPublicKey,
                    iacaSignerSpec = iacaSignerSpec,
                )

                assertEquals(
                    expected = blockingBundle.decodedCertificate.toDocumentSignerCertificateProfileData(),
                    actual = suspendBundle.decodedCertificate.toDocumentSignerCertificateProfileData(),
                )
                assertEquals(
                    expected = blockingBundle.decodedCertificate.issuerPrincipalName,
                    actual = suspendBundle.decodedCertificate.issuerPrincipalName,
                )

                val parsedBlockingFromBlocking = IsoSharedTestHarnessValidResources.dsParser.parseBlocking(
                    certificate = blockingBundle.certificateDer,
                )
                val parsedSuspendFromBlocking = IsoSharedTestHarnessValidResources.dsParser.parse(
                    certificate = blockingBundle.certificateDer,
                )

                assertEquals(
                    expected = parsedBlockingFromBlocking,
                    actual = parsedSuspendFromBlocking,
                )

                val parsedBlockingFromSuspend = IsoSharedTestHarnessValidResources.dsParser.parseBlocking(
                    certificate = suspendBundle.certificateDer,
                )
                val parsedSuspendFromSuspend = IsoSharedTestHarnessValidResources.dsParser.parse(
                    certificate = suspendBundle.certificateDer,
                )

                assertEquals(
                    expected = parsedBlockingFromSuspend,
                    actual = parsedSuspendFromSuspend,
                )

                IsoSharedTestHarnessValidResources.dsValidator.validateBlocking(
                    dsDecodedCert = parsedBlockingFromBlocking,
                    iacaDecodedCert = iacaBundle.decodedCertificate,
                )
                IsoSharedTestHarnessValidResources.dsValidator.validate(
                    dsDecodedCert = parsedSuspendFromBlocking,
                    iacaDecodedCert = iacaBundle.decodedCertificate,
                )
                IsoSharedTestHarnessValidResources.dsValidator.validateBlocking(
                    dsDecodedCert = parsedBlockingFromSuspend,
                    iacaDecodedCert = iacaBundle.decodedCertificate,
                )
                IsoSharedTestHarnessValidResources.dsValidator.validate(
                    dsDecodedCert = parsedSuspendFromSuspend,
                    iacaDecodedCert = iacaBundle.decodedCertificate,
                )
            }
        }.awaitAll()
    }
}
