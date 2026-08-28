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
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DocRequest
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocConsentPrompt
import id.walt.mdoc.proximity.MdocHolderRequestContext
import id.walt.mdoc.proximity.MdocResponseResolution
import id.walt.mdoc.proximity.ProximityException
import id.walt.wallet2.data.HolderKeyBindingErrorCode
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.withImportedHolderKeyBinding
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
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

class MobileWalletProximityRequestProcessorTest {
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
            assertEquals(null, processor.accept(prompt, submission))
            assertEquals(
                MobileWalletProximityHolderAuthorization(
                    exchange = 1,
                    requests = listOf(
                        MobileWalletProximityHolderAuthorizationRequest(
                            requestIndex = document.requestIndex,
                            credentialId = option.credentialId,
                            deviceAuthentication = MobileWalletProximityDeviceAuthenticationMethod.Signature,
                        )
                    ),
                ),
                processor.holderAuthorization(prompt, submission),
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

            assertEquals(null, processor.accept(prompt, submission))
            assertEquals(
                MobileWalletProximityDeviceAuthenticationMethod.Mac,
                processor.holderAuthorization(prompt, submission).requests.single().deviceAuthentication,
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
            assertEquals(null, processor.accept(prompt, submission))

            status = MobileWalletProximityCredentialStatus.Revoked

            val failure = kotlin.test.assertFailsWith<ProximityException> {
                processor.resolve(context, lowerPreview)
            }
            assertEquals("credential_unavailable", failure.error.code)
        }
    }

    private suspend fun fixture(
        holderSpec: KeySpec = KeySpec.Edwards(EdwardsCurve.ED25519),
        holderUsages: Set<KeyUsage> = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ): Fixture {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("proximity-holder-key"),
                spec = holderSpec,
                usages = holderUsages,
            )
        )
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
        block: suspend (Fixture) -> T,
    ): T {
        val fixture = fixture(holderSpec, holderUsages)
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
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(
                    "org.iso.18013.5.1" to JsonObject(
                        mapOf("given_name" to JsonPrimitive(if (id == "mdl-1") "Ada" else "Grace"))
                    )
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
    ): MdocHolderRequestContext = MdocHolderRequestContext(
            request = ExactCbor.of(
                request,
                coseCompliantCbor.encodeToByteArray(DeviceRequest.serializer(), request),
            ),
            transcript = ExactCbor.of(
                transcript,
                coseCompliantCbor.encodeToByteArray(SessionTranscript.serializer(), transcript),
            ),
            readerEphemeralKey = readerEphemeralKey,
            exchange = 1,
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

    private class Fixture(
        val wallet: Wallet,
        val runtime: CryptoRuntime,
        val readerEphemeralKey: ExactCbor<id.walt.cose.CoseKey>,
    ) {
        suspend fun close() = runtime.close()
    }
}
