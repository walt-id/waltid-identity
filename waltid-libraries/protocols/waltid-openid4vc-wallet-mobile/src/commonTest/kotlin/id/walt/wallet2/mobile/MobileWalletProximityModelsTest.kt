package id.walt.wallet2.mobile

import id.walt.mdoc.proximity.ProximityError as EngineProximityError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

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
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityReaderTrustDecision(
                state = MobileWalletProximityReaderTrustState.Trusted,
                certificatePath = MobileWalletProximityReaderCertificatePathState.UnknownAuthority,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityReaderTrustDecision(
                state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
                certificatePath = MobileWalletProximityReaderCertificatePathState.Invalid,
                revocation = MobileWalletProximityReaderRevocationState.Good,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityReaderTrustDecision(
                state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
                certificatePath = MobileWalletProximityReaderCertificatePathState.UnknownAuthority,
                rical = MobileWalletProximityRicalState.Matched,
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
            ProximityReaderTrustState.ValidButUntrusted,
            ricalEvidenceWithoutAutomaticTrust.state,
        )

        val directTrustWithUnavailableRical = MobileWalletProximityReaderTrustDecision(
            state = MobileWalletProximityReaderTrustState.Trusted,
            certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
            revocation = MobileWalletProximityReaderRevocationState.Good,
            rical = MobileWalletProximityRicalState.Unavailable,
        )
        assertEquals(MobileWalletProximityReaderTrustState.Trusted, directTrustWithUnavailableRical.state)

        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityReaderAuthentication(
            scope = MobileWalletProximityReaderAuthenticationScope.WholeRequest,
            outcome = MobileWalletProximityReaderAuthenticationOutcome.Valid(MobileWalletProximityReaderTrustDecision(
                state = MobileWalletProximityReaderTrustState.Trusted,
                certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
                revocation = MobileWalletProximityReaderRevocationState.Indeterminate,
            )),
        )
        }
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
        val review = review()

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

    @Test
    fun `authentication summary uses coverage and preserves failed statements`() {
        val first = review().documents.single()
        val review = review().copy(documents = listOf(first, first.copy(requestIndex = 1)))
        fun statement(scope: ProximityReaderAuthenticationScope, outcome: ProximityReaderAuthenticationOutcome) =
            ProximityReaderAuthentication(scope = scope, outcome = outcome)
        fun valid(scope: ProximityReaderAuthenticationScope, trust: ProximityReaderTrustState) =
            statement(scope, ProximityReaderAuthenticationOutcome.Valid(ProximityReaderTrustDecision(
                state = trust,
                certificatePath = if (trust == ProximityReaderTrustState.Trusted || trust == ProximityReaderTrustState.Revoked)
                    ProximityReaderCertificatePathState.Valid else ProximityReaderCertificatePathState.NotEvaluated,
                revocation = if (trust == ProximityReaderTrustState.Revoked) ProximityReaderRevocationState.Revoked
                    else ProximityReaderRevocationState.NotChecked,
            )))
        val whole = ProximityReaderAuthenticationScope.WholeRequest
        val document0 = ProximityReaderAuthenticationScope.Document(0)
        val document1 = ProximityReaderAuthenticationScope.Document(1)
        val absent0 = statement(document0, ProximityReaderAuthenticationOutcome.Absent)
        val absent1 = statement(document1, ProximityReaderAuthenticationOutcome.Absent)
        val trusted0 = valid(document0, ProximityReaderTrustState.Trusted)
        val trusted1 = valid(document1, ProximityReaderTrustState.Trusted)
        val trustedAll = valid(whole, ProximityReaderTrustState.Trusted)
        val cases = listOf(
            emptyList<ProximityReaderAuthentication>() to ProximityReaderAuthenticationSummary.Absent,
            listOf(absent0, absent1) to ProximityReaderAuthenticationSummary.Absent,
            listOf(trustedAll, absent0, absent1) to ProximityReaderAuthenticationSummary.Trusted,
            listOf(trusted0, absent1) to ProximityReaderAuthenticationSummary.Partial,
            listOf(trusted0, trusted1) to ProximityReaderAuthenticationSummary.Trusted,
            listOf(valid(whole, ProximityReaderTrustState.ValidButUntrusted), absent0, absent1) to ProximityReaderAuthenticationSummary.ValidButUntrusted,
            listOf(valid(whole, ProximityReaderTrustState.ValidButUntrusted), trusted0, trusted1) to ProximityReaderAuthenticationSummary.Trusted,
            listOf(trustedAll, statement(document0, ProximityReaderAuthenticationOutcome.Malformed("bad encoding"))) to ProximityReaderAuthenticationSummary.Malformed,
            listOf(trustedAll, statement(document0, ProximityReaderAuthenticationOutcome.Invalid("bad signature"))) to ProximityReaderAuthenticationSummary.Invalid,
            listOf(trustedAll, valid(document0, ProximityReaderTrustState.Revoked)) to ProximityReaderAuthenticationSummary.Revoked,
        )
        for ((statements, expected) in cases) {
            assertEquals(expected, review.copy(readerAuthentication = statements).readerAuthenticationSummary, statements.toString())
        }
    }

    @Test
    fun `no-data is terminal and retains the final request index`() {
        assertTrue(ProximityState.NoData(2).legalActions.isEmpty())
        assertEquals(2, ProximityState.NoData(2).exchange)
        assertFailsWith<IllegalArgumentException> { ProximityState.NoData(0) }
    }

    private fun review() = ProximityReview(
        reviewId = ProximityReviewId(Uuid.random().toString()),
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
                        validUntil = Instant.DISTANT_FUTURE,
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
}
