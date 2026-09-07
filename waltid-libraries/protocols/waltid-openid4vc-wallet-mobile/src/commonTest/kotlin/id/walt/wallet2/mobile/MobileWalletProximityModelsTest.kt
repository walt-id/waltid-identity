package id.walt.wallet2.mobile

import id.walt.mdoc.proximity.ProximityError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileWalletProximityModelsTest {
    @Test
    fun `EUDI profile requires trusted-reader policy`() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityConfiguration(
                profile = MobileWalletProximityProfile.EudiArf3Fcaf202608,
            )
        }

        MobileWalletProximityConfiguration(
            profile = MobileWalletProximityProfile.EudiArf3Fcaf202608,
            readerPolicy = MobileWalletProximityReaderPolicy.RequireTrusted,
        )
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityConfiguration(
                profile = MobileWalletProximityProfile.EudiArf3Fcaf202608,
                readerPolicy = MobileWalletProximityReaderPolicy.RequireTrusted,
                deviceAuthenticationPolicy = MobileWalletProximityDeviceAuthenticationPolicy.MacOnly,
            )
        }
    }

    @Test
    fun `transport capability keeps support dimensions independent and truthful`() {
        val unavailable = MobileWalletProximityError(
            category = MobileWalletProximityErrorCategory.Capability,
            code = "ble_powered_off",
            message = "Bluetooth is powered off",
            recovery = MobileWalletProximityRecovery.RetryPrerequisites,
        )
        val capability = MobileWalletProximityTransportCapability(
            implemented = true,
            profilePermitted = true,
            runtime = MobileWalletProximityRuntimeObservation.Unavailable(
                unavailable, listOf(MobileWalletProximityRemediationAction.EnableBluetooth),
            ),
            selected = true,
        )

        assertFalse(capability.mayStart)
        assertEquals(unavailable, capability.unavailable)
        assertTrue(capability.copy(runtime = MobileWalletProximityRuntimeObservation.Available).mayStart)
        val unchecked = capability.copy(runtime = MobileWalletProximityRuntimeObservation.NotChecked)
        assertFalse(unchecked.runtimeAvailable)
        assertEquals(null, unchecked.unavailable)
        assertTrue(unchecked.remediationActions.isEmpty())
        assertTrue(capability.copy(selected = false).runtime is MobileWalletProximityRuntimeObservation.Unavailable)
        val selectedButUnimplemented = capability.copy(implemented = false)
        assertTrue(selectedButUnimplemented.selected)
        assertFalse(selectedButUnimplemented.mayStart)
    }

    @Test
    fun `session may start with one usable selected engagement and retrieval method`() {
        val available = MobileWalletProximityTransportCapability(
            implemented = true,
            profilePermitted = true,
            runtime = MobileWalletProximityRuntimeObservation.Available,
            selected = true,
        )
        val unavailableAlternative = MobileWalletProximityTransportCapability(
            implemented = false,
            profilePermitted = true,
            runtime = MobileWalletProximityRuntimeObservation.NotChecked,
            selected = true,
        )
        val capabilities = MobileWalletProximityCapabilities(
            profile = MobileWalletProximityProfile.Iso180135Edition2Dis2026,
            qrEngagement = available,
            nfcEngagement = unavailableAlternative,
            bluetoothLowEnergy = available,
            nfcRetrieval = unavailableAlternative,
            wifiAwareRetrieval = unavailableAlternative.copy(selected = false),
        )

        assertTrue(capabilities.mayStart)
        assertTrue(capabilities.nfcEngagement.selected)
        assertFalse(capabilities.nfcEngagement.mayStart)
    }

    @Test
    fun `reader authentication scopes validate indices and invalid outcomes carry no trust`() {
        assertFailsWith<IllegalArgumentException> { MobileWalletProximityReaderAuthenticationScope.Document(-1) }
        val document = MobileWalletProximityReaderAuthenticationScope.Document(0)
        assertEquals(0, document.documentRequestIndex)
        assertEquals(null, MobileWalletProximityReaderAuthenticationScope.WholeRequest.documentRequestIndex)
        val absent = MobileWalletProximityReaderAuthentication(
            scope = document, outcome = MobileWalletProximityReaderAuthenticationOutcome.Absent,
        )
        assertEquals(MobileWalletProximityReaderTrustState.NotEvaluated, absent.trust)
        assertEquals(MobileWalletProximityReaderAuthenticationValidity.Absent, absent.validity)
    }

    @Test
    fun `reader trust facts reject contradictory states`() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityReaderTrustDecision(
                state = MobileWalletProximityReaderTrustState.Trusted,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityReaderTrustDecision(
                state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
                revocation = MobileWalletProximityReaderRevocationState.Revoked,
            )
        }

        val ricalEvidenceWithoutAutomaticTrust = MobileWalletProximityReaderTrustDecision(
            state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
            certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
            revocation = MobileWalletProximityReaderRevocationState.Good,
            rical = MobileWalletProximityRicalState.Matched,
            reason = "The configured policy does not establish reader trust",
        )
        assertEquals(
            MobileWalletProximityReaderTrustState.ValidButUntrusted,
            ricalEvidenceWithoutAutomaticTrust.state,
        )
    }

    @Test
    fun `application profile binding requires exact unpadded SHA-256 bytes`() {
        fun authorization(digest: String) = MobileWalletProximityApplicationAuthorization(
            profileId = "test-profile",
            displayTitle = "Test profile",
            details = listOf(MobileWalletProximityApplicationAuthorizationDetail("amount", "Amount", "EUR 1.00")),
            compatibleCredentialIds = setOf("credential-1"),
            resultBindingDigestBase64Url = digest,
        )

        authorization("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        assertFailsWith<IllegalArgumentException> {
            authorization("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        }
        assertFailsWith<IllegalArgumentException> { authorization("AA") }
    }

    @Test
    fun `lower-layer errors retain the precise wallet category`() {
        assertEquals(
            MobileWalletProximityErrorCategory.Trust,
            ProximityError.Policy("trusted_reader_required", "Trusted reader required").toWalletError().category,
        )
        assertEquals(
            MobileWalletProximityErrorCategory.StaleSubmission,
            ProximityError.Security("changed_submission", "Submission changed").toWalletError().category,
        )
        assertEquals(
            MobileWalletProximityErrorCategory.ApplicationProfile,
            ProximityError.Policy("application_profile_invalid", "Invalid profile").toWalletError().category,
        )
        assertEquals(
            MobileWalletProximityErrorCategory.HolderKey,
            ProximityError.Policy("holder_key_unavailable", "Holder key unavailable").toWalletError().category,
        )
        assertEquals(
            MobileWalletProximityErrorCategory.Protocol,
            ProximityError.Security("session_authentication_failed", "Session authentication failed")
                .toWalletError().category,
        )
    }

    @Test
    fun `legal actions are derived only from current state`() {
        val review = MobileWalletProximityReview(
            reviewId = MobileWalletProximityReviewId(kotlin.uuid.Uuid.random().toString()),
            exchange = 1,
            documents = listOf(
                MobileWalletProximityDocumentReview(
                    requestIndex = 0,
                    docType = "org.example.mdoc",
                    credentialOptions = listOf(
                        MobileWalletProximityCredentialOption(
                            credentialId = "credential-1",
                            label = "Example",
                            issuer = null,
                            validUntil = kotlin.time.Instant.DISTANT_FUTURE,
                            deviceAuthentication = MobileWalletProximityDeviceAuthenticationMethod.Signature,
                            requestedElements = listOf(
                                MobileWalletProximityRequestedElement(
                                    namespace = "org.example",
                                    elementIdentifier = "given_name",
                                    intentToRetain = false,
                                )
                            ),
                        )
                    ),
                )
            ),
            readerAuthentication = emptyList(),
            useCases = emptyList(),
            applicationAuthorizations = emptyList(),
        )

        assertEquals(
            setOf(
                MobileWalletProximityActionType.Approve,
                MobileWalletProximityActionType.Decline,
                MobileWalletProximityActionType.Cancel,
            ),
            MobileWalletProximityState.ReviewRequired(review).legalActions,
        )
        assertEquals(
            setOf(MobileWalletProximityActionType.Cancel),
            MobileWalletProximityState.AwaitingRequest(1).legalActions,
        )
        assertTrue(MobileWalletProximityState.Cancelled.legalActions.isEmpty())
    }
}
