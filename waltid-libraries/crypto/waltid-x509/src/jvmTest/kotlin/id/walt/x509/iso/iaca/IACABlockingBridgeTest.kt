package id.walt.x509.iso.iaca

import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import id.walt.x509.iso.assertIACABuilderDataEqualsCertificateData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IACABlockingBridgeTest {

    @Test
    fun `blocking and suspend build parse validate are equivalent when run sequentially`() = runTest {

        val profileData = IsoSharedTestHarnessValidResources.iacaProfileData
        val signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey()

        val blockingBundle = IsoSharedTestHarnessValidResources.iacaBuilder.buildBlocking(
            profileData = profileData,
            signingKey = signingKey,
        )

        val suspendBundle = IsoSharedTestHarnessValidResources.iacaBuilder.build(
            profileData = profileData,
            signingKey = signingKey,
        )

        assertIACABuilderDataEqualsCertificateData(profileData, blockingBundle.decodedCertificate)
        assertIACABuilderDataEqualsCertificateData(profileData, suspendBundle.decodedCertificate)

        assertEquals(
            expected = blockingBundle.decodedCertificate.toIACACertificateProfileData(),
            actual = suspendBundle.decodedCertificate.toIACACertificateProfileData(),
        )

        val parsedBlocking = IsoSharedTestHarnessValidResources.iacaParser.parseBlocking(
            certificate = blockingBundle.certificateDer,
        )

        val parsedSuspend = IsoSharedTestHarnessValidResources.iacaParser.parse(
            certificate = blockingBundle.certificateDer,
        )

        assertEquals(
            expected = parsedBlocking,
            actual = parsedSuspend,
        )

        IsoSharedTestHarnessValidResources.iacaValidator.validateBlocking(parsedBlocking)
        IsoSharedTestHarnessValidResources.iacaValidator.validate(parsedSuspend)

    }

    @Test
    fun `blocking and suspend build parse validate are equivalent when run concurrently`() = runTest {
        val profileData = IsoSharedTestHarnessValidResources.iacaProfileData
        val signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey()

        val blockingBuild = async(Dispatchers.Default) {
            IsoSharedTestHarnessValidResources.iacaBuilder.buildBlocking(
                profileData = profileData,
                signingKey = signingKey,
            )
        }
        val suspendBuild = async(Dispatchers.Default) {
            IsoSharedTestHarnessValidResources.iacaBuilder.build(
                profileData = profileData,
                signingKey = signingKey,
            )
        }

        val blockingBundle = blockingBuild.await()
        val suspendBundle = suspendBuild.await()

        assertIACABuilderDataEqualsCertificateData(profileData, blockingBundle.decodedCertificate)
        assertIACABuilderDataEqualsCertificateData(profileData, suspendBundle.decodedCertificate)

        assertEquals(
            expected = blockingBundle.decodedCertificate.toIACACertificateProfileData(),
            actual = suspendBundle.decodedCertificate.toIACACertificateProfileData(),
        )

        val parsedBlockingDeferred = async(Dispatchers.Default) {
            IsoSharedTestHarnessValidResources.iacaParser.parseBlocking(
                certificate = blockingBundle.certificateDer,
            )
        }
        val parsedSuspendDeferred = async(Dispatchers.Default) {
            IsoSharedTestHarnessValidResources.iacaParser.parse(
                certificate = blockingBundle.certificateDer,
            )
        }

        val parsedBlocking = parsedBlockingDeferred.await()
        val parsedSuspend = parsedSuspendDeferred.await()
        assertEquals(parsedBlocking, parsedSuspend)

        awaitAll(
            async(Dispatchers.Default) {
                IsoSharedTestHarnessValidResources.iacaValidator.validateBlocking(parsedBlocking)
            },
            async(Dispatchers.Default) {
                IsoSharedTestHarnessValidResources.iacaValidator.validate(parsedSuspend)
            },
        )

    }

    @Test
    fun `blocking and suspend apis handle consistently multiple concurrent invocations`() = runTest {
        val profileData = IsoSharedTestHarnessValidResources.iacaProfileData
        val signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey()

        List(12) {
            async(Dispatchers.Default) {
                val blockingBundle = IsoSharedTestHarnessValidResources.iacaBuilder.buildBlocking(
                    profileData = profileData,
                    signingKey = signingKey,
                )
                val suspendBundle = IsoSharedTestHarnessValidResources.iacaBuilder.build(
                    profileData = profileData,
                    signingKey = signingKey,
                )

                assertIACABuilderDataEqualsCertificateData(profileData, blockingBundle.decodedCertificate)
                assertIACABuilderDataEqualsCertificateData(profileData, suspendBundle.decodedCertificate)

                val parsedBlockingFromBlocking = IsoSharedTestHarnessValidResources.iacaParser.parseBlocking(
                    certificate = blockingBundle.certificateDer,
                )
                val parsedSuspendFromBlocking = IsoSharedTestHarnessValidResources.iacaParser.parse(
                    certificate = blockingBundle.certificateDer,
                )
                assertEquals(parsedBlockingFromBlocking, parsedSuspendFromBlocking)

                val parsedBlockingFromSuspend = IsoSharedTestHarnessValidResources.iacaParser.parseBlocking(
                    certificate = suspendBundle.certificateDer,
                )
                val parsedSuspendFromSuspend = IsoSharedTestHarnessValidResources.iacaParser.parse(
                    certificate = suspendBundle.certificateDer,
                )

                assertEquals(
                    expected = parsedBlockingFromSuspend,
                    actual = parsedSuspendFromSuspend,
                )

                IsoSharedTestHarnessValidResources.iacaValidator.validateBlocking(parsedBlockingFromBlocking)
                IsoSharedTestHarnessValidResources.iacaValidator.validate(parsedSuspendFromBlocking)
                IsoSharedTestHarnessValidResources.iacaValidator.validateBlocking(parsedBlockingFromSuspend)
                IsoSharedTestHarnessValidResources.iacaValidator.validate(parsedSuspendFromSuspend)

            }
        }.awaitAll()
    }
}
