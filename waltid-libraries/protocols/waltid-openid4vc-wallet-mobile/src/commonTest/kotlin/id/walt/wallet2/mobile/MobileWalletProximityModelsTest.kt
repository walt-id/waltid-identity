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
            recoverable = true,
        )
        val capability = MobileWalletProximityTransportCapability(
            implemented = true,
            profilePermitted = true,
            runtimeAvailable = false,
            selected = true,
            unavailable = unavailable,
            remediationActions = listOf(MobileWalletProximityRemediationAction.EnableBluetooth),
        )

        assertFalse(capability.mayStart)
        assertEquals(unavailable, capability.unavailable)
        assertFailsWith<IllegalArgumentException> {
            capability.copy(runtimeAvailable = true)
        }
        val selectedButUnimplemented = capability.copy(implemented = false)
        assertTrue(selectedButUnimplemented.selected)
        assertFalse(selectedButUnimplemented.mayStart)
    }

    @Test
    fun `session may start with one usable selected engagement and retrieval method`() {
        val available = MobileWalletProximityTransportCapability(
            implemented = true,
            profilePermitted = true,
            runtimeAvailable = true,
            selected = true,
        )
        val unavailableAlternative = MobileWalletProximityTransportCapability(
            implemented = false,
            profilePermitted = true,
            runtimeAvailable = false,
            selected = true,
            unavailable = MobileWalletProximityError(
                MobileWalletProximityErrorCategory.Capability,
                "not_implemented",
                "The selected alternative is not implemented",
                recoverable = false,
            ),
        )
        val capabilities = MobileWalletProximityCapabilities(
            profile = MobileWalletProximityProfile.Iso180135Edition2Dis2026,
            qrEngagement = available,
            nfcEngagement = unavailableAlternative,
            bluetoothLowEnergy = available,
            nfcRetrieval = unavailableAlternative,
            nfcV2Retrieval = unavailableAlternative.copy(selected = false),
            wifiAwareRetrieval = unavailableAlternative.copy(selected = false),
        )

        assertTrue(capabilities.mayStart)
        assertTrue(capabilities.nfcEngagement.selected)
        assertFalse(capabilities.nfcEngagement.mayStart)
    }

    @Test
    fun `NFCv2 retrieval cannot be selected without its NFC engagement path`() {
        val available = MobileWalletProximityTransportCapability(
            implemented = true,
            profilePermitted = true,
            runtimeAvailable = true,
            selected = true,
        )
        val unselected = available.copy(selected = false)

        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityCapabilities(
                profile = MobileWalletProximityProfile.Iso180135Edition2Dis2026,
                qrEngagement = available,
                nfcEngagement = unselected,
                bluetoothLowEnergy = unselected,
                nfcRetrieval = unselected,
                nfcV2Retrieval = available,
                wifiAwareRetrieval = unselected,
            )
        }
    }

    @Test
    fun `first-edition profile rejects NFCv2 instead of claiming unsupported compatibility`() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityConfiguration(
                profile = MobileWalletProximityProfile.Iso1801352021,
                engagement = MobileWalletProximityEngagementConfiguration.NfcOnly(
                    MobileWalletProximityNfcEngagementMode.ProvisionalV2()
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityConfiguration(
                profile = MobileWalletProximityProfile.Iso1801352021,
                engagement = MobileWalletProximityEngagementConfiguration.QrAndNfc(
                    MobileWalletProximityNfcEngagementMode.ProvisionalV2()
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(
                    bluetoothLowEnergy = MobileWalletProximityBleConfiguration(),
                ),
            )
        }
    }

    @Test
    fun `retrieval family is tied to its engagement family without dormant bearer state`() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityRetrievalConfiguration.Conventional(
                bluetoothLowEnergy = null,
                nfc = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.NfcOnly(
                    MobileWalletProximityNfcEngagementMode.ProvisionalV2()
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.Conventional(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.NfcOnly(
                    MobileWalletProximityNfcEngagementMode.Negotiated
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.NfcOnly(
                    MobileWalletProximityNfcEngagementMode.ProvisionalV2()
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(
                    qrNfc = MobileWalletProximityNfcRetrievalConfiguration(),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityConfiguration(
                engagement = MobileWalletProximityEngagementConfiguration.QrAndNfc(
                    MobileWalletProximityNfcEngagementMode.ProvisionalV2()
                ),
                retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(),
            )
        }

        MobileWalletProximityConfiguration(
            engagement = MobileWalletProximityEngagementConfiguration.NfcOnly(
                MobileWalletProximityNfcEngagementMode.ProvisionalV2()
            ),
            retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(),
        )
        MobileWalletProximityConfiguration(
            engagement = MobileWalletProximityEngagementConfiguration.QrAndNfc(
                MobileWalletProximityNfcEngagementMode.ProvisionalV2()
            ),
            retrieval = MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2(
                qrNfc = MobileWalletProximityNfcRetrievalConfiguration(),
            ),
        )
    }

    @Test
    fun `NFC length domains reject values outside their distinct wire limits`() {
        MobileWalletProximityNfcRetrievalConfiguration(
            maximumCommandDataLength = 255,
            maximumResponseDataLength = 256,
        )
        MobileWalletProximityNfcRetrievalConfiguration(
            maximumCommandDataLength = 65_535,
            maximumResponseDataLength = 65_536,
        )
        MobileWalletProximityNfcEngagementMode.ProvisionalV2(1)
        MobileWalletProximityNfcEngagementMode.ProvisionalV2(65_536)

        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityNfcRetrievalConfiguration(maximumCommandDataLength = 254)
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityNfcRetrievalConfiguration(maximumCommandDataLength = 65_536)
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityNfcRetrievalConfiguration(maximumResponseDataLength = 255)
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityNfcRetrievalConfiguration(maximumResponseDataLength = 65_537)
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityNfcEngagementMode.ProvisionalV2(0)
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityNfcEngagementMode.ProvisionalV2(65_537)
        }
    }

    @Test
    fun `reader authentication scope and document index cannot contradict each other`() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityReaderEvidence(
                scope = MobileWalletProximityReaderAuthenticationScope.Document,
                certificateChainDerBase64Url = listOf("AA"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletProximityReaderAuthentication(
                scope = MobileWalletProximityReaderAuthenticationScope.WholeRequest,
                documentRequestIndex = 0,
                validity = MobileWalletProximityReaderAuthenticationValidity.Absent,
                trust = MobileWalletProximityReaderTrustState.NotEvaluated,
            )
        }
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
            MobileWalletProximityReaderTrustState.ValidButUntrusted,
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
                documentRequestIndex = null,
                validity = MobileWalletProximityReaderAuthenticationValidity.Valid,
                trust = MobileWalletProximityReaderTrustState.Trusted,
                certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
                revocation = MobileWalletProximityReaderRevocationState.Indeterminate,
            )
        }
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
