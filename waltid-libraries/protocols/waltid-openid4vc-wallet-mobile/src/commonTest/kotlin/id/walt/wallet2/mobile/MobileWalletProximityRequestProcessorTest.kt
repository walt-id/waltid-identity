@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.createAndSignDetached
import id.walt.cose.toCoseKey
import id.walt.credentials.CredentialParser
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DocRequest
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.proximity.*
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.session.SessionEstablishment
import id.walt.mdoc.objects.session.SessionData
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocConsentPrompt
import id.walt.mdoc.proximity.MdocHolderRequestContext
import id.walt.mdoc.proximity.MdocResponseResolution
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.wallet2.data.HolderKeyBindingErrorCode
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.withImportedHolderKeyBinding
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.cbor.CborString
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.Signer
import id.walt.mdoc.proximity.MdocConsentDecision
import id.walt.mdoc.proximity.MdocSessionContinuation
import id.walt.mdoc.objects.mso.KeyAuthorization
import id.walt.x509.CertificateDer
import id.walt.x509.authorityKeyIdentifier
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MobileWalletProximityRequestProcessorTest {
    @Test
    fun `Appendix holder use-case matrix preserves request consent portrait and offline boundaries`() = runTest {
        data class Scenario(
            val id: String,
            val requested: List<String>,
            val disclosed: Set<String>,
            val expected: Set<String>,
        )

        val scenarios = listOf(
            Scenario(
                id = "UC_APPROVE_ALL_OFFLINE",
                requested = listOf("given_name", "family_name"),
                disclosed = setOf("given_name", "family_name"),
                expected = setOf("given_name", "family_name"),
            ),
            Scenario(
                id = "UC_REQUESTED_ONLY",
                requested = listOf("given_name"),
                disclosed = setOf("given_name"),
                expected = setOf("given_name"),
            ),
            Scenario(
                id = "UC_SELECTIVE_DENIAL",
                requested = listOf("given_name", "family_name"),
                disclosed = setOf("given_name"),
                expected = setOf("given_name"),
            ),
            Scenario(
                id = "UC_PORTRAIT_DENIAL",
                requested = listOf("portrait", "given_name"),
                disclosed = setOf("given_name"),
                expected = emptySet(),
            ),
        )

        withFixture { fixture ->
            scenarios.forEachIndexed { index, scenario ->
                val request = DeviceRequest(
                    version = DeviceRequest.VERSION,
                    docRequests = listOf(
                        DocRequest.fromValues(
                            docType = "org.iso.18013.5.1.mDL",
                            requestedElements = mapOf("org.iso.18013.5.1" to scenario.requested),
                            intentToRetain = false,
                        )
                    ),
                )
                val context = requestContext(request, transcript(), fixture.readerEphemeralKey)
                val processor = MobileWalletProximityRequestProcessor(
                    wallet = fixture.wallet,
                    configuration = MobileWalletProximityConfiguration(),
                    readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
                )
                val preview = processor.preview(context)
                val prompt = MdocConsentPrompt(
                    bindingToken = ImmutableBytes.of(ByteArray(32) { (index + 16).toByte() }),
                    exchange = context.exchange,
                    preview = preview,
                )
                val review = processor.review(prompt)
                val option = review.documents.single().credentialOptions.first()
                assertEquals(
                    scenario.requested.toSet(),
                    option.requestedElements.map { it.elementIdentifier }.toSet(),
                    scenario.id,
                )
                val submission = MobileWalletProximitySubmission(
                    documents = listOf(
                        MobileWalletProximityDocumentSubmission(
                            requestIndex = 0,
                            credentialId = option.credentialId,
                            disclosedElements = scenario.disclosed.mapTo(linkedSetOf()) {
                                MobileWalletProximityElementReference("org.iso.18013.5.1", it)
                            },
                        )
                    )
                )

                assertEquals(null, processor.accept(prompt, review.reviewId, submission), scenario.id)
                val resolution = assertIs<MdocResponseResolution.Send>(
                    processor.resolve(context, preview),
                    scenario.id,
                )
                val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(resolution.exactResponse.copy())
                val returned = response.documents.orEmpty().single().issuerSigned.namespaces.orEmpty()
                    .values
                    .flatMap { it.entries }
                    .mapTo(linkedSetOf()) { it.value.elementIdentifier }

                assertEquals(scenario.expected, returned, scenario.id)
            }
        }
    }

    @Test
    fun `domestic namespace request returns every approved element exactly once`() = runTest {
        withFixture { fixture ->
            val request = DeviceRequest(
                version = DeviceRequest.VERSION,
                docRequests = listOf(
                    DocRequest.fromValues(
                        docType = "org.iso.18013.5.1.mDL",
                        requestedElements = mapOf(DOMESTIC_NAMESPACE to DOMESTIC_ELEMENTS),
                        intentToRetain = false,
                    )
                ),
            )
            val context = requestContext(request, transcript(), fixture.readerEphemeralKey)
            val processor = MobileWalletProximityRequestProcessor(
                wallet = fixture.wallet,
                configuration = MobileWalletProximityConfiguration(),
                readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
            )
            val preview = processor.preview(context)
            val prompt = MdocConsentPrompt(
                bindingToken = ImmutableBytes.of(ByteArray(32) { 22 }),
                exchange = context.exchange,
                preview = preview,
            )
            val review = processor.review(prompt)
            val option = review.documents.single().credentialOptions.first()
            assertEquals(
                DOMESTIC_ELEMENTS.toSet(),
                option.requestedElements.mapTo(linkedSetOf()) { it.elementIdentifier },
                "UC_DOMESTIC_DATA:review",
            )
            val submission = MobileWalletProximitySubmission(
                documents = listOf(
                    MobileWalletProximityDocumentSubmission(
                        requestIndex = 0,
                        credentialId = option.credentialId,
                        disclosedElements = option.requestedElements.mapTo(linkedSetOf()) {
                            MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
                        },
                    )
                )
            )

            assertEquals(null, processor.accept(prompt, review.reviewId, submission), "UC_DOMESTIC_DATA:consent")
            val resolution = assertIs<MdocResponseResolution.Send>(processor.resolve(context, preview))
            val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(resolution.exactResponse.copy())
            val namespaces = response.documents.orEmpty().single().issuerSigned.namespaces.orEmpty()
            val returnedIdentifiers = namespaces.getValue(DOMESTIC_NAMESPACE).entries
                .map { it.value.elementIdentifier }

            assertEquals(setOf(DOMESTIC_NAMESPACE), namespaces.keys, "UC_DOMESTIC_DATA:namespaces")
            assertEquals(DOMESTIC_ELEMENTS.toSet(), returnedIdentifiers.toSet(), "UC_DOMESTIC_DATA:elements")
            assertEquals(
                returnedIdentifiers.size,
                returnedIdentifiers.distinct().size,
                "UC_DOMESTIC_DATA:no duplicate identifier within namespace",
            )
        }
    }

    @Test
    fun `multiple DocRequests are independently reviewed authorized and returned`() = runTest {
        withFixture { fixture ->
            val request = DeviceRequest(
                version = DeviceRequest.VERSION,
                docRequests = listOf("given_name", "family_name").map { identifier ->
                    DocRequest.fromValues(
                        docType = "org.iso.18013.5.1.mDL",
                        requestedElements = mapOf("org.iso.18013.5.1" to listOf(identifier)),
                        intentToRetain = false,
                    )
                },
            )
            val context = requestContext(request, transcript(), fixture.readerEphemeralKey)
            val processor = MobileWalletProximityRequestProcessor(
                wallet = fixture.wallet,
                configuration = MobileWalletProximityConfiguration(),
                readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
            )
            val preview = processor.preview(context)
            val prompt = MdocConsentPrompt(
                bindingToken = ImmutableBytes.of(ByteArray(32) { 21 }),
                exchange = context.exchange,
                preview = preview,
            )
            val review = processor.review(prompt)
            val submission = MobileWalletProximitySubmission(
                documents = review.documents.map { document ->
                    val option = document.credentialOptions.first()
                    MobileWalletProximityDocumentSubmission(
                        requestIndex = document.requestIndex,
                        credentialId = option.credentialId,
                        disclosedElements = option.requestedElements.mapTo(linkedSetOf()) {
                            MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
                        },
                    )
                }
            )

            assertEquals(null, processor.accept(prompt, review.reviewId, submission))
            val resolution = assertIs<MdocResponseResolution.Send>(processor.resolve(context, preview))
            val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(resolution.exactResponse.copy())

            assertEquals(2, review.documents.size, "UC_MULTIPLE_REQUESTS:review")
            assertEquals(2, response.documents.orEmpty().size, "UC_MULTIPLE_REQUESTS:response")
            assertEquals(
                setOf(setOf("given_name"), setOf("family_name")),
                response.documents.orEmpty().mapTo(linkedSetOf()) { document ->
                    document.issuerSigned.namespaces.orEmpty().values
                        .flatMap { it.entries }
                        .mapTo(linkedSetOf()) { it.value.elementIdentifier }
                },
                "UC_MULTIPLE_REQUESTS:elements",
            )
        }
    }

    @Test
    fun `review binds exact request profile constraint holder choice and response`() = runTest {
        withFixture { fixture ->
            val profile = RecordingProfile(compatibleCredentialId = "mdl-2")
            val processor = MobileWalletProximityRequestProcessor(
                wallet = fixture.wallet,
                configuration = MobileWalletProximityConfiguration(
                    applicationProfiles = MobileWalletProximityApplicationProfileRegistry(listOf(profile)),
                ),
                readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
            )
            val context = requestContext(fixture.readerEphemeralKey)
            val lowerPreview = processor.preview(context)
            val prompt = MdocConsentPrompt(
                bindingToken = ImmutableBytes.of(ByteArray(32) { 7 }),
                exchange = context.exchange,
                preview = lowerPreview,
            )

            val review = processor.review(prompt)

            val document = review.documents.single()
            val option = document.credentialOptions.single()
            assertEquals("mdl-2", option.credentialId)
            assertEquals(MobileWalletProximityDeviceAuthenticationMethod.Signature, option.deviceAuthentication)
            assertEquals(false, option.requestedElements.single().intentToRetain)
            assertEquals("org.iso.18013.5.1.mDL", profile.input?.requestedDocuments?.single()?.docType)
            assertTrue(profile.input?.readerAuthentication.orEmpty().isNotEmpty())
            assertEquals("test-profile", review.applicationAuthorizations.single().profileId)

            val submission = MobileWalletProximitySubmission(
                documents = listOf(
                    MobileWalletProximityDocumentSubmission(
                        requestIndex = document.requestIndex,
                        credentialId = option.credentialId,
                        disclosedElements = option.requestedElements.mapTo(linkedSetOf()) {
                            MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
                        },
                    )
                )
            )
            assertEquals(null, processor.accept(prompt, review.reviewId, submission))
            assertEquals(
                MobileWalletProximityHolderAuthorization(
                    reviewId = review.reviewId,
                    exchange = 1,
                    requests = listOf(
                        MobileWalletProximityHolderAuthorizationRequest(
                            requestIndex = document.requestIndex,
                            credentialId = option.credentialId,
                            deviceAuthentication = MobileWalletProximityDeviceAuthenticationMethod.Signature,
                        )
                    ),
                ),
                processor.holderAuthorization(review.reviewId),
            )
            val resolution = assertIs<MdocResponseResolution.Send>(processor.resolve(context, lowerPreview))
            val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(resolution.exactResponse.copy())
            assertIs<DeviceAuth.Signature>(response.documents?.single()?.deviceSigned?.deviceAuth)
            assertEquals(lowerPreview.submissionBindingDigest, resolution.submissionBindingDigest)
        }
    }

    @Test
    fun `signature preference falls back to MAC before review when the holder key cannot sign`() = runTest {
        withFixture(
            holderSpec = KeySpec.Ec(EcCurve.P256),
            holderUsages = setOf(KeyUsage.KEY_AGREEMENT),
        ) { fixture ->
            val processor = MobileWalletProximityRequestProcessor(
                wallet = fixture.wallet,
                configuration = MobileWalletProximityConfiguration(
                    deviceAuthenticationPolicy =
                        MobileWalletProximityDeviceAuthenticationPolicy.PreferSignature,
                ),
                readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
            )
            val context = requestContext(fixture.readerEphemeralKey)
            val lowerPreview = processor.preview(context)
            val prompt = MdocConsentPrompt(
                bindingToken = ImmutableBytes.of(ByteArray(32) { 8 }),
                exchange = context.exchange,
                preview = lowerPreview,
            )
            val review = processor.review(prompt)
            val option = review.documents.single().credentialOptions.first()
            assertEquals(MobileWalletProximityDeviceAuthenticationMethod.Mac, option.deviceAuthentication)
            val submission = submissionFor(review, option)

            assertEquals(null, processor.accept(prompt, review.reviewId, submission))
            assertEquals(
                MobileWalletProximityDeviceAuthenticationMethod.Mac,
                processor.holderAuthorization(review.reviewId).requests.single().deviceAuthentication,
            )
            val resolution = assertIs<MdocResponseResolution.Send>(processor.resolve(context, lowerPreview))
            val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(resolution.exactResponse.copy())
            assertIs<DeviceAuth.Mac>(response.documents?.single()?.deviceSigned?.deviceAuth)
        }
    }

    @Test
    fun `strict signature policy does not switch to MAC`() = runTest {
        withFixture(
            holderSpec = KeySpec.Ec(EcCurve.P256),
            holderUsages = setOf(KeyUsage.KEY_AGREEMENT),
        ) { fixture ->
            val processor = MobileWalletProximityRequestProcessor(
                wallet = fixture.wallet,
                configuration = MobileWalletProximityConfiguration(
                    deviceAuthenticationPolicy =
                        MobileWalletProximityDeviceAuthenticationPolicy.SignatureOnly,
                ),
                readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
            )

            val failure = kotlin.test.assertFailsWith<ProximityException> {
                processor.preview(requestContext(fixture.readerEphemeralKey))
            }

            assertEquals("holder_key_unavailable", failure.error.code)
        }
    }

    @Test
    fun `invalid persisted holder key binding uses stable safe error`() = runTest {
        withFixture { fixture ->
            val wallet = fixture.wallet.copy(
                credentialStores = listOf(
                    FailingCredentialStore(
                        HolderKeyBindingException(
                            code = HolderKeyBindingErrorCode.BINDING_INVALID,
                            credentialId = "corrupt-mdoc",
                            message = "sensitive persistence diagnostic",
                        )
                    )
                )
            )
            val processor = MobileWalletProximityRequestProcessor(
                wallet = wallet,
                configuration = MobileWalletProximityConfiguration(),
                readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
            )

            val failure = kotlin.test.assertFailsWith<ProximityException> {
                processor.preview(requestContext(fixture.readerEphemeralKey))
            }

            assertEquals("holder_key_unavailable", failure.error.code)
            assertTrue("sensitive persistence diagnostic" !in failure.error.message)
        }
    }

    @Test
    fun `multiple whole-request authentications retain independent trust decisions`() = runTest {
        withFixture { fixture ->
            val observedIndices = mutableListOf<Int>()
            val processor = MobileWalletProximityRequestProcessor(
                wallet = fixture.wallet,
                configuration = MobileWalletProximityConfiguration(
                    readerTrustEvaluator = MobileWalletProximityReaderTrustEvaluator { evidence ->
                        observedIndices += evidence.authenticationIndex
                        if (evidence.authenticationIndex == 0) {
                            MobileWalletProximityReaderTrustDecision(
                                state = MobileWalletProximityReaderTrustState.Trusted,
                                certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
                            )
                        } else {
                            MobileWalletProximityReaderTrustDecision(
                                state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
                            )
                        }
                    },
                ),
                readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
            )
            val context = signedWholeRequestContext(fixture)
            val lowerPreview = processor.preview(context)
            val review = processor.review(
                MdocConsentPrompt(
                    bindingToken = ImmutableBytes.of(ByteArray(32) { 10 }),
                    exchange = context.exchange,
                    preview = lowerPreview,
                )
            )

            assertEquals(listOf(0, 1), observedIndices)
            val wholeRequest = review.readerAuthentication.filter {
                it.scope == MobileWalletProximityReaderAuthenticationScope.WholeRequest
            }
            assertEquals(listOf(0, 1), wholeRequest.map { it.authenticationIndex })
            assertEquals(
                listOf(
                    MobileWalletProximityReaderTrustState.Trusted,
                    MobileWalletProximityReaderTrustState.ValidButUntrusted,
                ),
                wholeRequest.map { it.trust },
            )
        }
    }

    @Test
    fun `application profile failures use stable safe error codes`() = runTest {
        withFixture { fixture ->
            val context = requestContext(fixture.readerEphemeralKey)
            val scenarios = listOf(
                listOf(
                    TestProfile("registered") {
                        MobileWalletProximityApplicationProfileResult.Recognized(
                            profileAuthorization("different")
                        )
                    }
                ) to "application_profile_invalid",
                listOf(
                    TestProfile("failing") { error("sensitive adapter diagnostic") }
                ) to "application_profile_failed",
                listOf(
                    TestProfile("first") {
                        MobileWalletProximityApplicationProfileResult.Recognized(profileAuthorization("first"))
                    },
                    TestProfile("second") {
                        MobileWalletProximityApplicationProfileResult.Recognized(profileAuthorization("second"))
                    },
                ) to "application_profile_ambiguous",
            )

            scenarios.forEach { (profiles, expectedCode) ->
                val processor = MobileWalletProximityRequestProcessor(
                    wallet = fixture.wallet,
                    configuration = MobileWalletProximityConfiguration(
                        applicationProfiles = MobileWalletProximityApplicationProfileRegistry(profiles),
                    ),
                    readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
                )
                val failure = kotlin.test.assertFailsWith<ProximityException> {
                    processor.preview(context)
                }
                assertEquals(expectedCode, failure.error.code)
                assertTrue("sensitive adapter diagnostic" !in failure.error.message)
            }
        }
    }

    @Test
    fun `status change after consent fails closed before response`() = runTest {
        withFixture { fixture ->
            var status = MobileWalletProximityCredentialStatus.Valid
            val processor = MobileWalletProximityRequestProcessor(
                wallet = fixture.wallet,
                configuration = MobileWalletProximityConfiguration(
                    credentialStatusEvaluator = MobileWalletProximityCredentialStatusEvaluator { status },
                ),
                readerAuthenticationAlgorithms = setOf(Cose.Algorithm.ES256),
            )
            val context = requestContext(fixture.readerEphemeralKey)
            val lowerPreview = processor.preview(context)
            val prompt = MdocConsentPrompt(
                bindingToken = ImmutableBytes.of(ByteArray(32) { 9 }),
                exchange = context.exchange,
                preview = lowerPreview,
            )
            val review = processor.review(prompt)
            val option = review.documents.single().credentialOptions.first()
            val submission = MobileWalletProximitySubmission(
                listOf(
                    MobileWalletProximityDocumentSubmission(
                        requestIndex = 0,
                        credentialId = option.credentialId,
                        disclosedElements = option.requestedElements.mapTo(linkedSetOf()) {
                            MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
                        },
                    )
                )
            )
            assertEquals(null, processor.accept(prompt, review.reviewId, submission))

            status = MobileWalletProximityCredentialStatus.Revoked

            val failure = kotlin.test.assertFailsWith<ProximityException> {
                processor.resolve(context, lowerPreview)
            }
            assertEquals("credential_unavailable", failure.error.code)
        }
    }

    @Test
    fun `reader trust change after approval prevents response`() = runTest {
        withFixture { fixture ->
            var trusted = true
            val processor = processor(fixture, MobileWalletProximityConfiguration(
                readerTrustEvaluator = MobileWalletProximityReaderTrustEvaluator {
                    if (trusted) MobileWalletProximityReaderTrustDecision(
                        MobileWalletProximityReaderTrustState.Trusted,
                        certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
                    ) else MobileWalletProximityReaderTrustDecision(MobileWalletProximityReaderTrustState.ValidButUntrusted)
                },
            ))
            val context = signedWholeRequestContext(fixture)
            val preview = processor.preview(context)
            val prompt = prompt(preview, 1)
            val review = processor.review(prompt)
            assertEquals(null, processor.accept(prompt, review.reviewId, submissionFor(review, review.documents.single().credentialOptions.first())))
            trusted = false
            val failure = kotlin.test.assertFailsWith<ProximityException> { processor.resolve(context, preview) }
            assertEquals("changed_submission", failure.error.code)
        }
    }

    @Test
    fun `application authorization change after approval prevents response`() = runTest {
        withFixture { fixture ->
            var authorization = profileAuthorization("changing-profile")
            val profile = TestProfile("changing-profile") { MobileWalletProximityApplicationProfileResult.Recognized(authorization) }
            val processor = processor(fixture, MobileWalletProximityConfiguration(
                applicationProfiles = MobileWalletProximityApplicationProfileRegistry(listOf(profile)),
            ))
            val context = requestContext(fixture.readerEphemeralKey)
            val preview = processor.preview(context)
            val prompt = prompt(preview, 1)
            val review = processor.review(prompt)
            assertEquals(null, processor.accept(prompt, review.reviewId, submissionFor(review, review.documents.single().credentialOptions.single())))
            authorization = authorization.copy(details = listOf(MobileWalletProximityApplicationAuthorizationDetail("amount", "Amount", "EUR 2.00")))
            val failure = kotlin.test.assertFailsWith<ProximityException> { processor.resolve(context, preview) }
            assertEquals("changed_submission", failure.error.code)
        }
    }

    @Test
    fun `removing approved credential prevents substitution with another matching credential`() = runTest {
        withFixture { fixture ->
            val processor = processor(fixture)
            val context = requestContext(fixture.readerEphemeralKey)
            val preview = processor.preview(context)
            val prompt = prompt(preview, 1)
            val review = processor.review(prompt)
            val option = review.documents.single().credentialOptions.first()
            assertEquals(null, processor.accept(prompt, review.reviewId, submissionFor(review, option)))
            fixture.wallet.credentialStores.forEach { it.removeCredential(option.credentialId) }
            val failure = kotlin.test.assertFailsWith<ProximityException> { processor.resolve(context, preview) }
            assertEquals("changed_submission", failure.error.code)
        }
    }

    @Test
    fun `review identity rejects replay and duplicate decisions while preserving next exchanges`() = runTest {
        withFixture { fixture ->
            val processor = processor(fixture)
            val owner = owner(fixture, processor)
            val firstContext = requestContext(fixture.readerEphemeralKey)
            val firstPreview = processor.preview(firstContext)
            val firstPrompt = prompt(firstPreview, 1)
            val firstDecision = async(start = CoroutineStart.UNDISPATCHED) { owner.decide(firstPrompt) }
            val firstReview = assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value).review
            val submission = submissionFor(firstReview, firstReview.documents.single().credentialOptions.first())
                .copy(continueAfterResponse = true)
            val invalid = submission.copy(documents = submission.documents.map { it.copy(credentialId = "not-offered") })
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Approve(firstReview.reviewId, invalid)))
            assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value)
            assertEquals(MobileWalletProximityActionResult.Accepted, owner.dispatch(MobileWalletProximityAction.Approve(firstReview.reviewId, submission)))
            assertIs<MdocConsentDecision.Approve>(firstDecision.await())
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Approve(firstReview.reviewId, submission.copy(continueAfterResponse = false))))
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Decline(firstReview.reviewId)))
            val resolution = assertIs<MdocResponseResolution.Send>(processor.resolve(firstContext, firstPreview))
            assertEquals(MdocSessionContinuation.CONTINUE, resolution.continuation)

            val secondContext = requestContext(unsignedRequest(), transcript(), fixture.readerEphemeralKey, exchange = 2)
            val secondPreview = processor.preview(secondContext)
            val secondDecision = async(start = CoroutineStart.UNDISPATCHED) { owner.decide(prompt(secondPreview, 2)) }
            val secondReview = assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value).review
            kotlin.test.assertNotEquals(firstReview.reviewId, secondReview.reviewId)
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Approve(firstReview.reviewId, submission)))
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Decline(firstReview.reviewId)))
            owner.publish(MobileWalletProximityState.AwaitingRequest(2))
            owner.publish(MobileWalletProximityState.SendingResponse(1))
            owner.publish(MobileWalletProximityState.AwaitingNextRequest(1))
            owner.publish(MobileWalletProximityState.Completed(1, false))
            assertEquals(secondReview.reviewId, assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value).review.reviewId)
            assertEquals(MobileWalletProximityActionResult.Accepted, owner.dispatch(MobileWalletProximityAction.Decline(secondReview.reviewId)))
            assertIs<MdocConsentDecision.Deny>(secondDecision.await())
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Decline(secondReview.reviewId)))
            owner.publish(MobileWalletProximityState.Completed(2, true))
            owner.publish(MobileWalletProximityState.Preparing(MobileWalletProximityProfile.Iso180135Edition2Dis2026))
            assertIs<MobileWalletProximityState.Completed>(owner.state.value)
        }
    }

    @Test
    fun `older engine observations never replace a dispatchable review or authorization`() = runTest {
        withFixture { fixture ->
            val processor = processor(fixture)
            val owner = owner(fixture, processor)
            val preview = processor.preview(requestContext(fixture.readerEphemeralKey))
            val decision = async(start = CoroutineStart.UNDISPATCHED) { owner.decide(prompt(preview, 1)) }
            val review = assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value).review
            owner.publish(MobileWalletProximityState.AwaitingRequest(1))
            assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value)
            owner.dispatch(MobileWalletProximityAction.Approve(review.reviewId, submissionFor(review, review.documents.single().credentialOptions.first())))
            decision.await()
            owner.publish(MobileWalletProximityState.AwaitingRequest(1))
            assertIs<MobileWalletProximityState.AuthorizingHolderKey>(owner.state.value)
            owner.cancel()
        }
    }

    @Test
    fun `cross-session actions and cancellation cannot revive a pending review`() = runTest {
        withFixture { fixture ->
            val first = processor(fixture)
            val firstContext = requestContext(fixture.readerEphemeralKey)
            val firstPrompt = prompt(first.preview(firstContext), 1)
            val oldReview = first.review(firstPrompt)
            val second = processor(fixture)
            val owner = owner(fixture, second)
            val secondPreview = second.preview(firstContext)
            val decision = async(start = CoroutineStart.UNDISPATCHED) { owner.decide(prompt(secondPreview, 1)) }
            val current = assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value).review
            val submission = submissionFor(current, current.documents.single().credentialOptions.first())
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Approve(oldReview.reviewId, submission)))
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Decline(oldReview.reviewId)))
            assertEquals(MobileWalletProximityActionResult.Accepted, owner.dispatch(MobileWalletProximityAction.Cancel))
            decision.join()
            kotlin.test.assertTrue(decision.isCancelled)
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Approve(current.reviewId, submission)))
            owner.publish(MobileWalletProximityState.SendingResponse(1))
            owner.publish(MobileWalletProximityState.Failed(ProximityError.Policy("late", "Late failure").toWalletError()))
            assertEquals(MobileWalletProximityState.Cancelled, owner.state.value)
            first.cancel()
        }
    }

    @Test
    fun `accepted choice survives input and exported review mutation during fresh-state suspension`() = runTest {
        withFixture { fixture ->
            val freshEntered = CompletableDeferred<Unit>()
            val resumeFresh = CompletableDeferred<Unit>()
            var suspendFresh = false
            val processor = processor(fixture, MobileWalletProximityConfiguration(
                credentialStatusEvaluator = MobileWalletProximityCredentialStatusEvaluator {
                    if (suspendFresh) { freshEntered.complete(Unit); resumeFresh.await() }
                    MobileWalletProximityCredentialStatus.Valid
                },
            ))
            val context = requestContext(requestWithNames("given_name", "family_name"), transcript(), fixture.readerEphemeralKey)
            val preview = processor.preview(context)
            val prompt = prompt(preview, 1)
            val review = processor.review(prompt)
            val selected = linkedSetOf(MobileWalletProximityElementReference("org.iso.18013.5.1", "given_name"))
            val documents = mutableListOf(MobileWalletProximityDocumentSubmission(0, "mdl-1", selected))
            val submission = MobileWalletProximitySubmission(documents, continueAfterResponse = true)
            assertEquals(null, processor.accept(prompt, review.reviewId, submission))
            val authorization = processor.holderAuthorization(review.reviewId)
            runCatching { (review.documents.single().credentialOptions as MutableList).clear() }
            val retainedReview = processor.review(prompt)
            assertEquals(setOf("mdl-1", "mdl-2"), retainedReview.documents.single().credentialOptions.map { it.credentialId }.toSet())
            suspendFresh = true
            val response = async { processor.resolve(context, preview) }
            freshEntered.await()
            selected += MobileWalletProximityElementReference("org.iso.18013.5.1", "family_name")
            documents[0] = MobileWalletProximityDocumentSubmission(0, "mdl-2", selected)
            runCatching { (review.documents.single().credentialOptions as MutableList).clear() }
            runCatching { (authorization.requests as MutableList).clear() }
            resumeFresh.complete(Unit)
            val sent = assertIs<MdocResponseResolution.Send>(response.await())
            assertEquals(MdocSessionContinuation.CONTINUE, sent.continuation)
            val disclosed = decodeResponse(sent).documents!!.single().issuerSigned.namespaces!!.getValue("org.iso.18013.5.1").entries
            assertEquals(listOf("given_name"), disclosed.map { it.value.elementIdentifier })
            assertEquals("Ada", assertIs<CborString>(disclosed.single().value.elementValue).value)
        }
    }

    @Test
    fun `approval before cancellation never accepts a later decline or sends a response`() = runTest {
        withFixture { fixture ->
            val processor = processor(fixture)
            val owner = owner(fixture, processor)
            val context = requestContext(fixture.readerEphemeralKey)
            val preview = processor.preview(context)
            val decision = async(start = CoroutineStart.UNDISPATCHED) { owner.decide(prompt(preview, 1)) }
            val review = assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value).review
            val submission = submissionFor(review, review.documents.single().credentialOptions.first())
            assertEquals(MobileWalletProximityActionResult.Accepted, owner.dispatch(MobileWalletProximityAction.Approve(review.reviewId, submission)))
            assertEquals(MobileWalletProximityActionResult.Accepted, owner.dispatch(MobileWalletProximityAction.Cancel))
            assertIs<MdocConsentDecision.Approve>(decision.await())
            assertIs<MobileWalletProximityActionResult.Rejected>(owner.dispatch(MobileWalletProximityAction.Decline(review.reviewId)))
            kotlin.test.assertFailsWith<IllegalArgumentException> { processor.resolve(context, preview) }
            assertEquals(MobileWalletProximityState.Cancelled, owner.state.value)
        }
    }

    @Test
    fun `cancellation during protected signing clears approval and allows a fresh session`() = runTest {
        val signingEntered = CompletableDeferred<Unit>()
        val releaseSigning = CompletableDeferred<Unit>()
        var blockSigning = true
        withFixture(holderTransform = { original ->
            object : Key by original {
                override val capabilities: KeyCapabilities = original.capabilities.copy(signer = Signer { data, algorithm ->
                    if (blockSigning) { signingEntered.complete(Unit); releaseSigning.await() }
                    requireNotNull(original.capabilities.signer).sign(data, algorithm)
                })
            }
        }) { fixture ->
            val processor = processor(fixture)
            val owner = owner(fixture, processor)
            val context = requestContext(fixture.readerEphemeralKey)
            val preview = processor.preview(context)
            val decision = async(start = CoroutineStart.UNDISPATCHED) { owner.decide(prompt(preview, 1)) }
            val review = assertIs<MobileWalletProximityState.ReviewRequired>(owner.state.value).review
            assertEquals(MobileWalletProximityActionResult.Accepted, owner.dispatch(MobileWalletProximityAction.Approve(
                review.reviewId, submissionFor(review, review.documents.single().credentialOptions.first()),
            )))
            decision.await()
            val response = async { processor.resolve(context, preview) }
            signingEntered.await()
            owner.dispatch(MobileWalletProximityAction.Cancel)
            response.cancelAndJoin()
            releaseSigning.complete(Unit)
            assertTrue(response.isCancelled)
            assertEquals(MobileWalletProximityState.Cancelled, owner.state.value)
            blockSigning = false
            val fresh = processor(fixture)
            val freshPreview = fresh.preview(context)
            val freshPrompt = prompt(freshPreview, 1)
            val freshReview = fresh.review(freshPrompt)
            assertEquals(null, fresh.accept(freshPrompt, freshReview.reviewId, submissionFor(freshReview, freshReview.documents.single().credentialOptions.first())))
            assertIs<MdocResponseResolution.Send>(fresh.resolve(context, freshPreview))
        }
    }

    @Test
    fun `profile input and result projections cannot alter retained application authorization`() = runTest {
        val signingEntered = CompletableDeferred<Unit>()
        val releaseSigning = CompletableDeferred<Unit>()
        withFixture(holderTransform = { original ->
            object : Key by original {
                override val capabilities = original.capabilities.copy(signer = Signer { data, algorithm ->
                    signingEntered.complete(Unit)
                    releaseSigning.await()
                    requireNotNull(original.capabilities.signer).sign(data, algorithm)
                })
            }
        }) { fixture ->
            val details = mutableListOf(MobileWalletProximityApplicationAuthorizationDetail("amount", "Amount", "EUR 1.00"))
            val compatible = linkedSetOf("mdl-1")
            val elements = mutableListOf(MobileWalletProximityDeviceSignedElement(
                "mdl-1", "org.example.application", "amount", byteArrayOf(0x01).encodeToBase64Url(),
            ))
            val returned = profileAuthorization("owned-profile").copy(details = details, compatibleCredentialIds = compatible, deviceSignedElements = elements)
            var evaluations = 0
            val profile = object : MobileWalletProximityApplicationProfile {
                override val id = "owned-profile"
                override suspend fun evaluate(input: MobileWalletProximityApplicationProfileInput): MobileWalletProximityApplicationProfileResult {
                    evaluations++
                    runCatching { (input.readerAuthentication as MutableList).clear() }
                    return MobileWalletProximityApplicationProfileResult.Recognized(returned)
                }
            }
            val processor = processor(fixture, MobileWalletProximityConfiguration(applicationProfiles = MobileWalletProximityApplicationProfileRegistry(listOf(profile))))
            val context = requestContext(fixture.readerEphemeralKey)
            val preview = processor.preview(context)
            val prompt = prompt(preview, 1)
            val review = processor.review(prompt)
            assertTrue(review.readerAuthentication.isNotEmpty())
            runCatching { (review.applicationAuthorizations.single().details as MutableList).clear() }
            val stable = processor.review(prompt)
            assertEquals("EUR 1.00", stable.applicationAuthorizations.single().details.single().value)
            assertEquals(null, processor.accept(prompt, stable.reviewId, submissionFor(stable, stable.documents.single().credentialOptions.single())))
            val response = async { processor.resolve(context, preview) }
            signingEntered.await()
            details[0] = MobileWalletProximityApplicationAuthorizationDetail("amount", "Amount", "EUR 9.00")
            compatible += "mdl-2"
            elements[0] = elements[0].copy(valueCborBase64Url = byteArrayOf(0x09).encodeToBase64Url())
            releaseSigning.complete(Unit)
            val sent = assertIs<MdocResponseResolution.Send>(response.await())
            val deviceValues = decodeResponse(sent).documents!!.single().deviceSigned!!.namespaces.value.entries.getValue("org.example.application").entries
            assertEquals("amount", deviceValues.single().key)
            assertEquals(1L, (deviceValues.single().value as Number).toLong())
            assertEquals(2, evaluations)
        }
    }

    @Test
    fun `mutating cached credential projections cannot replace authoritative signed claims`() = runTest {
        withFixture { fixture ->
            val original = assertIs<id.walt.credentials.formats.MdocsCredential>(fixture.wallet.findCredential("mdl-1")!!.credential)
            val cached = original.document.issuerSigned.namespaces!!
            (cached as MutableMap).clear()
            val processor = processor(fixture)
            val context = requestContext(fixture.readerEphemeralKey)
            val preview = processor.preview(context)
            val prompt = prompt(preview, 1)
            val review = processor.review(prompt)
            val option = review.documents.single().credentialOptions.single { it.credentialId == "mdl-1" }
            assertEquals(null, processor.accept(prompt, review.reviewId, submissionFor(review, option)))
            val sent = assertIs<MdocResponseResolution.Send>(processor.resolve(context, preview))
            val elements = decodeResponse(sent).documents!!.single().issuerSigned.namespaces!!.getValue("org.iso.18013.5.1").entries
            assertEquals("Ada", assertIs<CborString>(elements.single().value.elementValue).value)
        }
    }

    @Test
    fun `issuer identifier matches an intermediate AKI and malformed chains still fail validation`() = runTest {
        withFixture { fixture ->
            suspend fun key(name: String) = fixture.runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId(name), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
            val rootKey = key("aki-root")
            val intermediateKey = key("aki-intermediate")
            val signerKey = key("aki-signer")
            val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
            val root = X509CertificateUtil.createSelfSignedCertificate(rootKey, algorithm) { subjectDn = "CN=AKI Root" }
            val intermediate = X509CertificateUtil.createCertificate(rootKey, root, algorithm) {
                subjectDn = "CN=AKI Intermediate"
                subjectPublicKey(intermediateKey)
                extensionBasicConstraints { critical = true; cA = true; pathLenConstraint = 0 }
                extensionKeyUsage { critical = true; addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign) }
                extensionSubjectKeyIdentifier()
            }
            val signer = X509CertificateUtil.createCertificate(intermediateKey, intermediate, algorithm) {
                profileDocumentSignerCertificate(
                    crlDistributionPointUri = "https://issuer.example/crl", issuerUri = "https://issuer.example",
                    subjectKey = signerKey, subjectDnCountryCode = "AT", subjectDnCommonName = "AKI Signer",
                )
            }
            val intermediateAki = requireNotNull(CertificateDer(intermediate.encodedDer.toByteArray()).authorityKeyIdentifier)
            val leafAki = requireNotNull(CertificateDer(signer.encodedDer.toByteArray()).authorityKeyIdentifier)
            kotlin.test.assertFalse(intermediateAki.contentEquals(leafAki))
            val stored = fixture.wallet.findCredential("mdl-1")!!
            val existing = stored.credential as id.walt.credentials.formats.MdocsCredential
            val issuerSigned = MdocIssuer.issueUniversal(
                issuerKey = signerKey, signatureAlgorithm = Cose.Algorithm.ES256,
                issuerCertificate = listOf(signer, intermediate).map { CoseCertificate(it.encodedDer.toByteArray()) },
                holderKey = existing.documentMso.deviceKeyInfo.deviceKey,
                docType = existing.docType,
                data = MdocIssuer.MdocUniversalIssuanceData(mapOf("org.iso.18013.5.1" to JsonObject(mapOf("given_name" to JsonPrimitive("Ada"))))),
            )
            val document = Document(existing.docType, issuerSigned)
            suspend fun install(value: Document) {
                val signed = coseCompliantCbor.encodeToByteArray(Document.serializer(), value).encodeToBase64Url()
                fixture.wallet.addCredential(stored.copy(credential = CredentialParser.detectAndParse(signed).second))
            }
            install(document)
            fun context(aki: ByteArray): MdocHolderRequestContext {
                val ordinary = unsignedRequest()
                val requested = ordinary.docRequests.single()
                val constrained = requested.copy(itemsRequest = id.walt.mdoc.encoding.ByteStringWrapper(
                    requested.itemsRequest.value.copy(requestInfo = id.walt.mdoc.objects.deviceretrieval.DocRequestInfo(issuerIdentifiers = listOf(aki))),
                ))
                return requestContext(ordinary.copy(docRequests = listOf(constrained)), transcript(), fixture.readerEphemeralKey)
            }
            val processor = processor(fixture)
            val matching = context(intermediateAki)
            val preview = processor.preview(matching)
            val review = processor.review(prompt(preview, 1))
            assertEquals(listOf("mdl-1"), review.documents.single().credentialOptions.map { it.credentialId })
            processor.cancel()
            val noMatch = kotlin.test.assertFailsWith<ProximityException> { processor(fixture).preview(context(ByteArray(20) { 99 })) }
            assertEquals("request_unsatisfied", noMatch.error.code)
            val malformed = document.copy(issuerSigned = id.walt.mdoc.objects.document.IssuerSigned.fromIssuerSignedLists(
                namespaces = issuerSigned.namespaces.orEmpty(), issuerAuth = issuerSigned.issuerAuth.copy(
                unprotected = issuerSigned.issuerAuth.unprotected.copy(x5chain = listOf(CoseCertificate(intermediate.encodedDer.toByteArray()))),
            )))
            install(malformed)
            val invalid = kotlin.test.assertFailsWith<ProximityException> { processor(fixture).preview(matching) }
            assertEquals("credential_unavailable", invalid.error.code)
        }
    }

    @Test
    fun `unknown document and namespace requests return no data without consent`() = runTest {
        withFixture { fixture ->
            for ((id, request) in listOf(
                "mDL_MS_DR_UF_07" to DeviceRequest("unknown.document", mapOf("org.iso.18013.5.1" to listOf("given_name"))),
                "mDL_MS_DR_UF_10" to DeviceRequest("org.iso.18013.5.1.mDL", mapOf("unknown.namespace" to listOf("given_name"))),
            )) {
                val result = wireExchange(fixture, request)
                assertIs<MdocHolderSessionResult.Completed>(result.result, id)
                assertEquals(0, result.consentCalls, id)
                assertEquals(0u, result.response.status, id)
                assertEquals(null, result.response.documents, id)
            }
        }
    }

    @Test
    fun `mixed known and unknown fields return only approved known elements over the encrypted session`() = runTest {
        withFixture { fixture ->
            for (denyFamilyName in listOf(false, true)) {
                val result = wireExchange(fixture, requestWithNames("given_name", "family_name", "unknown_element"), denyFamilyName = denyFamilyName)
                val id = if (denyFamilyName) "mDL_MS_DR_UF_13" else "mDL_MS_DR_UF_12"
                assertIs<MdocHolderSessionResult.Completed>(result.result, id)
                assertEquals(1, result.consentCalls, id)
                assertEquals(0u, result.response.status, id)
                val names = result.response.documents!!.single().issuerSigned.namespaces!!.getValue("org.iso.18013.5.1").entries
                    .map { it.value.elementIdentifier }.toSet()
                assertEquals(if (denyFamilyName) setOf("given_name") else setOf("given_name", "family_name"), names, id)
            }
        }
    }

    private data class WireOutcome(val result: MdocHolderSessionResult, val response: DeviceResponse, val consentCalls: Int)

    private suspend fun wireExchange(
        fixture: Fixture,
        request: DeviceRequest,
        configuration: MobileWalletProximityConfiguration = MobileWalletProximityConfiguration(),
        denyFamilyName: Boolean = false,
    ): WireOutcome {
        suspend fun key(id: String) = fixture.runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.KEY_AGREEMENT),
        ))
        val deviceKey = key("wire-holder-ephemeral")
        val readerKey = key("wire-reader-ephemeral")
        val method = DeviceRetrievalMethod.Nfc(1024u, 1024u)
        val context = EngagementContext(MdocProximityProfile.ISO_18013_5_ED2_DIS_2026, 1_048_576, MdocEngagementMode.Qr)
        val capabilities = MdocSessionCapabilities.forSession(context.profile, deviceKey, emptySet())
        val engagement = MdocDeviceEngagementFactory().create(deviceKey, listOf(method), context, capabilities)
        val readerPublic = (readerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val readerBytes = coseCompliantCbor.encodeToByteArray(readerPublic)
        val transcript = SessionTranscript.forQr(engagement.engagement.encodedCopy(), readerBytes)
        val cipher = MdocSessionCipher.establishForReader(readerKey, engagement.engagement.value.security.eDeviceKey.value,
            MdocCryptoHelper.buildSessionTranscriptBytes(transcript))
        val processor = processor(fixture, configuration)
        val loopback = FakeProximityLoopback.create()
        var consentCalls = 0
        val engine = MdocHolderProtocolEngine(deviceKey, listOf(FakeTransportProvider(method, loopback.holder)), processor,
            MdocConsentHandler { prompt ->
                consentCalls++
                val review = processor.review(prompt)
                val option = review.documents.single().credentialOptions.first()
                val all = submissionFor(review, option)
                val submission = if (!denyFamilyName) all else all.copy(documents = all.documents.map { document ->
                    document.copy(disclosedElements = document.disclosedElements.filterNot { it.elementIdentifier == "family_name" }.toSet())
                })
                assertEquals(null, processor.accept(prompt, review.reviewId, submission))
                MdocConsentDecision.Approve(prompt.bindingToken)
            }, context, capabilities)
        try {
            loopback.reader.send(ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(SessionEstablishment(
                ByteStringWrapper(readerPublic, readerBytes), cipher.encrypt(coseCompliantCbor.encodeToByteArray(request)),
            ))))
            val result = engine.run()
            val messages = mutableListOf<SessionData>()
            while (true) messages += coseCompliantCbor.decodeFromByteArray<SessionData>((loopback.reader.receive() ?: break).copy())
            assertEquals(20u, messages.last().status)
            val encrypted = messages.mapNotNull { it.data }.single()
            return WireOutcome(result, coseCompliantCbor.decodeFromByteArray<DeviceResponse>(cipher.decrypt(encrypted)), consentCalls)
        } finally {
            cipher.close()
            processor.cancel()
        }
    }

    private fun processor(fixture: Fixture, configuration: MobileWalletProximityConfiguration = MobileWalletProximityConfiguration()) =
        MobileWalletProximityRequestProcessor(fixture.wallet, configuration, setOf(Cose.Algorithm.ES256))

    private suspend fun owner(fixture: Fixture, processor: MobileWalletProximityRequestProcessor): MobileWalletProximitySessionOwner =
        MobileWalletProximitySessionOwner(
            MobileWalletProximityState.CheckingPrerequisites(MobileWalletProximityCoordinator(fixture.wallet, null).capabilities(MobileWalletProximityConfiguration())),
            Channel(Channel.CONFLATED),
        ).also { it.attach(processor) }

    private fun prompt(preview: id.walt.mdoc.proximity.MdocRequestPreview, exchange: Int) =
        MdocConsentPrompt(ImmutableBytes.of(ByteArray(32) { exchange.toByte() }), exchange, preview)

    private fun decodeResponse(response: MdocResponseResolution.Send): DeviceResponse =
        coseCompliantCbor.decodeFromByteArray(response.exactResponse.copy())

    private fun requestWithNames(vararg names: String) = DeviceRequest(
        version = DeviceRequest.VERSION,
        docRequests = listOf(DocRequest.fromValues("org.iso.18013.5.1.mDL", mapOf("org.iso.18013.5.1" to names.toList()), false)),
    )

    private suspend fun fixture(
        holderSpec: KeySpec = KeySpec.Edwards(EdwardsCurve.ED25519),
        holderUsages: Set<KeyUsage> = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        holderTransform: (Key) -> Key = { it },
    ): Fixture {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val holderKey = holderTransform(runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("proximity-holder-key"),
                spec = holderSpec,
                usages = holderUsages,
            )
        ))
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("proximity-issuer-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val rootKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("proximity-root-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val readerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("proximity-reader-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val certificateAlgorithm =
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val rootCertificate = X509CertificateUtil.createSelfSignedCertificate(rootKey, certificateAlgorithm) {
            subjectDn = "CN=Proximity wallet test root"
        }
        val documentSignerCertificate = X509CertificateUtil.createCertificate(
            issuerKey = rootKey,
            issuerCert = rootCertificate,
            signatureAlgorithm = certificateAlgorithm,
        ) {
            profileDocumentSignerCertificate(
                crlDistributionPointUri = "https://issuer.example/crl",
                issuerUri = "https://issuer.example",
                subjectKey = issuerKey,
                subjectDnCountryCode = "AT",
                subjectDnOrganizationName = "walt.id test",
                subjectDnCommonName = "Proximity wallet test issuer",
            )
        }
        val keyStore = InMemoryKeyStore().also { it.addCrypto2Key(holderKey) }
        val credentialStore = InMemoryCredentialStore()
        val wallet = Wallet(
            id = "proximity-request-processor",
            keyStores = listOf(keyStore),
            credentialStores = listOf(credentialStore),
        )
        listOf("mdl-1", "mdl-2").forEach { credentialId ->
            wallet.addCredential(
                wallet.withImportedHolderKeyBinding(
                    issueCredential(credentialId, holderKey, issuerKey, documentSignerCertificate)
                )
            )
        }
        val readerPublic = assertIs<EncodedKey.Jwk>(
            assertNotNull(readerKey.capabilities.publicKeyExporter).exportPublicKey()
        ).toCoseKey()
        return Fixture(
            wallet = wallet,
            runtime = runtime,
            readerEphemeralKey = ExactCbor.of(
                readerPublic,
                coseCompliantCbor.encodeToByteArray(id.walt.cose.CoseKey.serializer(), readerPublic),
            ),
        )
    }

    private suspend fun <T> withFixture(
        holderSpec: KeySpec = KeySpec.Edwards(EdwardsCurve.ED25519),
        holderUsages: Set<KeyUsage> = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        holderTransform: (Key) -> Key = { it },
        block: suspend (Fixture) -> T,
    ): T {
        val fixture = fixture(holderSpec, holderUsages, holderTransform)
        return try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private suspend fun issueCredential(
        id: String,
        holderKey: Key,
        issuerKey: Key,
        documentSignerCertificate: id.walt.certificate.x509.X509Certificate,
    ): StoredCredential {
        val holderPublicJwk = assertIs<EncodedKey.Jwk>(
            assertNotNull(holderKey.capabilities.publicKeyExporter).exportPublicKey()
        )
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(documentSignerCertificate.encodedDer.toByteArray())),
            holderKey = holderPublicJwk.toCoseKey(),
            docType = "org.iso.18013.5.1.mDL",
            keyAuthorizations = KeyAuthorization(namespaces = listOf("org.example.application")),
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(
                    "org.iso.18013.5.1" to JsonObject(
                        mapOf(
                            "given_name" to JsonPrimitive(if (id == "mdl-1") "Ada" else "Grace"),
                            "family_name" to JsonPrimitive(if (id == "mdl-1") "Lovelace" else "Hopper"),
                            "portrait" to JsonPrimitive("AQID"),
                        )
                    ),
                    DOMESTIC_NAMESPACE to JsonObject(
                        mapOf(
                            DOMESTIC_ELEMENTS[0] to JsonPrimitive("AT-9-001"),
                            DOMESTIC_ELEMENTS[1] to JsonPrimitive("resident"),
                        )
                    ),
                )
            ),
        )
        val raw = coseCompliantCbor.encodeToByteArray(
            Document.serializer(),
            Document(docType = "org.iso.18013.5.1.mDL", issuerSigned = issuerSigned),
        ).encodeToBase64Url()
        return StoredCredential(
            id = id,
            credential = CredentialParser.detectAndParse(raw).second,
            label = id,
        )
    }

    private fun requestContext(
        readerEphemeralKey: ExactCbor<id.walt.cose.CoseKey>,
    ): MdocHolderRequestContext {
        val request = unsignedRequest()
        val transcript = transcript()
        return requestContext(request, transcript, readerEphemeralKey)
    }

    private suspend fun signedWholeRequestContext(fixture: Fixture): MdocHolderRequestContext {
        val transcript = transcript()
        val unsigned = unsignedRequest()
        val key = fixture.runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("proximity-reader-authentication-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            key,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) { subjectDn = "CN=Proximity reader authentication test" }
        val authentication = CoseSign1.createAndSignDetached(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
            unprotectedHeaders = CoseHeaders(
                x5chain = listOf(CoseCertificate(certificate.encodedDer.toByteArray()))
            ),
            detachedPayload = ReaderAuthenticationPayloads.forAllDocuments(
                transcript,
                unsigned.docRequests.map { it.itemsRequest },
                unsigned.deviceRequestInfo,
            ),
            key = key,
        )
        val signed = unsigned.copy(
            version = DeviceRequest.VERSION_WITH_SIGNING,
            readerAuthAll = listOf(authentication, authentication),
        )
        return requestContext(signed, transcript, fixture.readerEphemeralKey)
    }

    private fun unsignedRequest(): DeviceRequest = DeviceRequest(
            version = DeviceRequest.VERSION,
            docRequests = listOf(
                DocRequest.fromValues(
                    docType = "org.iso.18013.5.1.mDL",
                    requestedElements = mapOf("org.iso.18013.5.1" to listOf("given_name")),
                    intentToRetain = false,
                )
            ),
        )

    private fun transcript(): SessionTranscript =
        SessionTranscript.forQr(ByteArray(32) { 1 }, ByteArray(32) { 2 })

    private fun requestContext(
        request: DeviceRequest,
        transcript: SessionTranscript,
        readerEphemeralKey: ExactCbor<id.walt.cose.CoseKey>,
        exchange: Int = 1,
    ): MdocHolderRequestContext = MdocHolderRequestContext(
            request = ExactCbor.of(
                request,
                coseCompliantCbor.encodeToByteArray(DeviceRequest.serializer(), request),
            ),
            transcript = ExactCbor.of(
                transcript,
                id.walt.mdoc.crypto.MdocCryptoHelper.buildSessionTranscriptBytes(transcript),
            ),
            readerEphemeralKey = readerEphemeralKey,
            exchange = exchange,
        )

    private fun submissionFor(
        review: MobileWalletProximityReview,
        option: MobileWalletProximityCredentialOption,
    ): MobileWalletProximitySubmission = MobileWalletProximitySubmission(
        documents = listOf(
            MobileWalletProximityDocumentSubmission(
                requestIndex = review.documents.single().requestIndex,
                credentialId = option.credentialId,
                disclosedElements = option.requestedElements.mapTo(linkedSetOf()) {
                    MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
                },
            )
        )
    )

    private class RecordingProfile(
        private val compatibleCredentialId: String,
    ) : MobileWalletProximityApplicationProfile {
        override val id: String = "test-profile"
        var input: MobileWalletProximityApplicationProfileInput? = null

        override suspend fun evaluate(
            input: MobileWalletProximityApplicationProfileInput,
        ): MobileWalletProximityApplicationProfileResult {
            this.input = input
            return MobileWalletProximityApplicationProfileResult.Recognized(
                MobileWalletProximityApplicationAuthorization(
                    profileId = id,
                    displayTitle = "Test authorization",
                    details = listOf(
                        MobileWalletProximityApplicationAuthorizationDetail(
                            id = "amount",
                            label = "Amount",
                            value = "EUR 1.00",
                        )
                    ),
                    compatibleCredentialIds = setOf(compatibleCredentialId),
                    resultBindingDigestBase64Url = Base64.UrlSafe
                        .withPadding(Base64.PaddingOption.ABSENT)
                        .encode(ByteArray(32) { 3 }),
                )
            )
        }
    }

    private class TestProfile(
        override val id: String,
        private val evaluate: suspend () -> MobileWalletProximityApplicationProfileResult,
    ) : MobileWalletProximityApplicationProfile {
        override suspend fun evaluate(
            input: MobileWalletProximityApplicationProfileInput,
        ): MobileWalletProximityApplicationProfileResult = evaluate()
    }

    private class FailingCredentialStore(
        private val failure: HolderKeyBindingException,
    ) : WalletCredentialStore {
        override suspend fun getCredential(id: String): StoredCredential? = null

        override suspend fun listCredentials(): Flow<StoredCredential> = flow { throw failure }

        override suspend fun addCredential(entry: StoredCredential) = error("Not supported in this test")

        override suspend fun removeCredential(id: String): Boolean = false
    }

    private fun profileAuthorization(profileId: String): MobileWalletProximityApplicationAuthorization =
        MobileWalletProximityApplicationAuthorization(
            profileId = profileId,
            displayTitle = "Test authorization",
            details = listOf(
                MobileWalletProximityApplicationAuthorizationDetail("amount", "Amount", "EUR 1.00")
            ),
            compatibleCredentialIds = setOf("mdl-1"),
            resultBindingDigestBase64Url = Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .encode(ByteArray(32) { 4 }),
        )

    private companion object {
        const val DOMESTIC_NAMESPACE = "org.iso.18013.5.1.AT"
        val DOMESTIC_ELEMENTS = listOf("domestic_admin_code", "domestic_resident_status")
    }

    private class Fixture(
        val wallet: Wallet,
        val runtime: CryptoRuntime,
        val readerEphemeralKey: ExactCbor<id.walt.cose.CoseKey>,
    ) {
        suspend fun close() = runtime.close()
    }
}
