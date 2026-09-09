package id.walt.wallet2.mobile

import id.walt.mdoc.proximity.ProximityError as EngineProximityError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProximityModelsTest {
    @Test
    fun `EUDI profile requires trusted-reader policy`() {
        assertFailsWith<IllegalArgumentException> {
            ProximityConfiguration(
                profile = ProximityProfile.EudiArf3Fcaf202608,
            )
        }

        ProximityConfiguration(
            profile = ProximityProfile.EudiArf3Fcaf202608,
            readerPolicy = ProximityReaderPolicy.RequireTrusted,
        )
        assertFailsWith<IllegalArgumentException> {
            ProximityConfiguration(
                profile = ProximityProfile.EudiArf3Fcaf202608,
                readerPolicy = ProximityReaderPolicy.RequireTrusted,
                deviceAuthenticationPolicy = ProximityDeviceAuthenticationPolicy.MacOnly,
            )
        }
    }

    @Test
    fun `transport capability keeps support dimensions independent and truthful`() {
        val unavailable = ProximityError(
            category = ProximityErrorCategory.Capability,
            code = "ble_powered_off",
            message = "Bluetooth is powered off",
            recovery = ProximityRecovery.RetryPrerequisites,
        )
        val capability = ProximityTransportCapability(
            implemented = true,
            profilePermitted = true,
            runtime = ProximityRuntimeObservation.Unavailable(
                unavailable, listOf(ProximityRemediationAction.EnableBluetooth),
            ),
            selected = true,
        )

        assertFalse(capability.mayStart)
        assertEquals(unavailable, capability.unavailable)
        assertTrue(capability.copy(runtime = ProximityRuntimeObservation.Available).mayStart)
        val unchecked = capability.copy(runtime = ProximityRuntimeObservation.NotChecked)
        assertFalse(unchecked.runtimeAvailable)
        assertEquals(null, unchecked.unavailable)
        assertTrue(unchecked.remediationActions.isEmpty())
        assertTrue(capability.copy(selected = false).runtime is ProximityRuntimeObservation.Unavailable)
        val selectedButUnimplemented = capability.copy(implemented = false)
        assertTrue(selectedButUnimplemented.selected)
        assertFalse(selectedButUnimplemented.mayStart)
    }

    @Test
    fun `session may start with one usable selected engagement and retrieval method`() {
        val available = ProximityTransportCapability(
            implemented = true,
            profilePermitted = true,
            runtime = ProximityRuntimeObservation.Available,
            selected = true,
        )
        val unavailableAlternative = ProximityTransportCapability(
            implemented = false,
            profilePermitted = true,
            runtime = ProximityRuntimeObservation.NotChecked,
            selected = true,
        )
        val capabilities = ProximityCapabilities(
            profile = ProximityProfile.Iso180135Edition2Dis2026,
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
        assertFailsWith<IllegalArgumentException> { ProximityReaderAuthenticationScope.Document(-1) }
        val document = ProximityReaderAuthenticationScope.Document(0)
        assertEquals(0, document.documentRequestIndex)
        assertEquals(null, ProximityReaderAuthenticationScope.WholeRequest.documentRequestIndex)
        val absent = ProximityReaderAuthentication(
            scope = document, outcome = ProximityReaderAuthenticationOutcome.Absent,
        )
        assertEquals(ProximityReaderTrustState.NotEvaluated, absent.trust)
        assertEquals(ProximityReaderAuthenticationValidity.Absent, absent.validity)
    }

    @Test
    fun `reader trust facts reject contradictory states`() {
        assertFailsWith<IllegalArgumentException> {
            ProximityReaderTrustDecision(
                state = ProximityReaderTrustState.Trusted,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProximityReaderTrustDecision(
                state = ProximityReaderTrustState.ValidButUntrusted,
                revocation = ProximityReaderRevocationState.Revoked,
            )
        }

        val ricalEvidenceWithoutAutomaticTrust = ProximityReaderTrustDecision(
            state = ProximityReaderTrustState.ValidButUntrusted,
            certificatePath = ProximityReaderCertificatePathState.Valid,
            revocation = ProximityReaderRevocationState.Good,
            rical = ProximityRicalState.Matched,
            reason = "The configured policy does not establish reader trust",
        )
        assertEquals(
            ProximityReaderTrustState.ValidButUntrusted,
            ricalEvidenceWithoutAutomaticTrust.state,
        )
    }

    @Test
    fun `application profile binding requires exact unpadded SHA-256 bytes`() {
        fun authorization(digest: String) = ProximityApplicationAuthorization(
            profileId = "test-profile",
            displayTitle = "Test profile",
            details = listOf(ProximityApplicationAuthorizationDetail("amount", "Amount", "EUR 1.00")),
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
            ProximityErrorCategory.Trust,
            EngineProximityError.Policy("trusted_reader_required", "Trusted reader required").toWalletError().category,
        )
        assertEquals(
            ProximityErrorCategory.StaleSubmission,
            EngineProximityError.Security("changed_submission", "Submission changed").toWalletError().category,
        )
        assertEquals(
            ProximityErrorCategory.ApplicationProfile,
            EngineProximityError.Policy("application_profile_invalid", "Invalid profile").toWalletError().category,
        )
        assertEquals(
            ProximityErrorCategory.HolderKey,
            EngineProximityError.Policy("holder_key_unavailable", "Holder key unavailable").toWalletError().category,
        )
        assertEquals(
            ProximityErrorCategory.Protocol,
            EngineProximityError.Security("session_authentication_failed", "Session authentication failed")
                .toWalletError().category,
        )
    }

    @Test
    fun `legal actions are derived only from current state`() {
        val review = ProximityReview(
            reviewId = ProximityReviewId(kotlin.uuid.Uuid.random().toString()),
            exchange = 1,
            documents = listOf(
                ProximityDocumentReview(
                    requestIndex = 0,
                    docType = "org.example.mdoc",
                    credentialOptions = listOf(
                        ProximityCredentialOption(
                            credentialId = "credential-1",
                            label = "Example",
                            issuer = null,
                            validUntil = kotlin.time.Instant.DISTANT_FUTURE,
                            deviceAuthentication = ProximityDeviceAuthenticationMethod.Signature,
                            requestedElements = listOf(
                                ProximityRequestedElement(
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
                ProximityActionType.Approve,
                ProximityActionType.Decline,
                ProximityActionType.Cancel,
            ),
            ProximityState.ReviewRequired(review).legalActions,
        )
        assertEquals(
            setOf(ProximityActionType.Cancel),
            ProximityState.AwaitingRequest(1).legalActions,
        )
        assertTrue(ProximityState.Cancelled.legalActions.isEmpty())
    }
}
