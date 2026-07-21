package id.walt.wallet2.mobile

import id.walt.cose.toCoseVerifier
import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.MdocsExamples
import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MobileWalletTest {

    @Test
    fun presentationErrorCodesMatchOAuthAndOpenId4VpValues() {
        assertEquals(
            listOf(
                "access_denied",
                "invalid_request",
                "invalid_client",
                "invalid_scope",
                "unauthorized_client",
                "unsupported_response_type",
                "server_error",
                "temporarily_unavailable",
                "vp_formats_not_supported",
                "invalid_request_uri_method",
                "invalid_transaction_data",
                "wallet_unavailable",
            ),
            MobileWalletPresentationErrorCode.entries.map { it.errorCode },
        )
    }

    @Test
    fun mobileWalletConfigUsesStableDefaults() {
        val config = MobileWalletConfig()

        assertEquals("default", config.walletId)
        assertEquals(MobileWalletKeyType.secp256r1, config.defaultKeyType)
        assertEquals(null, config.attestationConfig)
        assertEquals(MobileWalletPersistence(), config.persistence)
        assertEquals(emptyList(), config.transactionDataProfiles)
        assertIs<MobileWalletDatabaseKey.Managed>(config.persistence.databaseKey)
        assertEquals(MobileWalletStores(), config.persistence.stores)
    }

    @Test
    fun mobileWalletConfigAcceptsCustomTransactionDataProfiles() {
        val profiles = listOf(
            MobileWalletTransactionDataProfile(
                type = "example.transaction",
                displayName = "Example Transaction",
                fields = listOf("amount"),
            )
        )

        val config = MobileWalletConfig(transactionDataProfiles = profiles)

        assertEquals(profiles, config.transactionDataProfiles)
        val registry = profiles.toTransactionDataTypeRegistry()
        assertEquals(setOf("example.transaction"), registry.types)
    }

    @Test
    fun persistenceCanCombineProvidedDatabaseKeyWithIndependentStoreOverrides() {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val databaseKeyProvider = RecordingDatabaseKeyProvider()
        val keys = MobileWalletKeys(
            store = keyStore,
            generate = { error("Existing custom-store wallets should not generate a new key") },
        )

        val persistence = MobileWalletPersistence(
            databaseKey = MobileWalletDatabaseKey.Provided(databaseKeyProvider),
            stores = MobileWalletStores(
                credentials = credentialStore,
                dids = didStore,
                keys = keys,
            ),
        )

        assertSame(databaseKeyProvider, assertIs<MobileWalletDatabaseKey.Provided>(persistence.databaseKey).provider)
        assertSame(credentialStore, persistence.stores.credentials)
        assertSame(didStore, persistence.stores.dids)
        assertSame(keyStore, persistence.stores.keys?.store)
        assertSame(keys.generate, persistence.stores.keys?.generate)
    }

    @Test
    fun walletCanUseInjectedStoresAndAtomicKeyConfiguration() = runTest {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val keys = MobileWalletKeys(
            store = keyStore,
            generate = { error("Existing custom-store wallets should not generate a new key") },
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = keys.store,
            didStore = didStore,
            credentialStore = credentialStore,
            keyGenerator = keys.generate,
        )

        val bootstrap = wallet.bootstrap()

        assertEquals("custom-key", bootstrap.keyId)
        assertEquals("did:key:custom", bootstrap.did)
        assertEquals(1, keyStore.listKeysCalls)
        assertEquals(1, didStore.listDidsCalls)
    }

    @Test
    fun deleteWalletRemovesEntriesFromActiveStores() = runTest {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = keyStore,
            didStore = didStore,
            credentialStore = credentialStore,
            keyGenerator = { error("deleteWallet should not generate a key") },
        )

        wallet.deleteWallet()

        assertEquals(listOf("custom-key"), keyStore.removedKeyIds)
        assertEquals(listOf("did:key:custom"), didStore.removedDids)
        assertEquals(emptyList(), credentialStore.removedCredentialIds)
    }

    @Test
    fun mobileWalletKeyTypeMapsToCryptoKeyTypeInternally() {
        assertEquals(KeyType.Ed25519, MobileWalletKeyType.Ed25519.toKeyType())
        assertEquals(KeyType.secp256k1, MobileWalletKeyType.secp256k1.toKeyType())
        assertEquals(KeyType.secp256r1, MobileWalletKeyType.secp256r1.toKeyType())
        assertEquals(KeyType.secp384r1, MobileWalletKeyType.secp384r1.toKeyType())
        assertEquals(KeyType.secp521r1, MobileWalletKeyType.secp521r1.toKeyType())
        assertEquals(KeyType.RSA, MobileWalletKeyType.RSA.toKeyType())
        assertEquals(KeyType.RSA3072, MobileWalletKeyType.RSA3072.toKeyType())
        assertEquals(KeyType.RSA4096, MobileWalletKeyType.RSA4096.toKeyType())
    }

    @Test
    fun walletSessionEventsMapToMobileWalletEventsInCommonCode() {
        val progress = WalletSessionEvent.issuance_offer_resolved.toMobileWalletEvent()
        val prepared = WalletSessionEvent.presentation_response_prepared.toMobileWalletEvent()
        val completed = WalletSessionEvent.presentation_completed.toMobileWalletEvent()
        val failed = WalletSessionEvent.issuance_failed.toMobileWalletEvent()

        assertEquals(MobileWalletEventPhase.issuance, progress.phase)
        assertEquals(MobileWalletEventStatus.progress, progress.status)
        assertEquals("issuance_offer_resolved", progress.name)

        assertEquals(MobileWalletEventPhase.presentation, prepared.phase)
        assertEquals(MobileWalletEventStatus.progress, prepared.status)
        assertEquals("presentation_response_prepared", prepared.name)

        assertEquals(MobileWalletEventPhase.presentation, completed.phase)
        assertEquals(MobileWalletEventStatus.completed, completed.status)
        assertEquals("presentation_completed", completed.name)

        assertEquals(MobileWalletEventPhase.issuance, failed.phase)
        assertEquals(MobileWalletEventStatus.failed, failed.status)
        assertEquals("issuance_failed", failed.name)
    }

    @Test
    fun presentationResultCarriesVerifierResponseAsJsonString() {
        val result = MobileWalletPresentationResult.Transmitted.Succeeded(
            verifierResponseJson = """{"accepted":true}""",
            redirectUrl = "wallet://return",
        )

        assertEquals("""{"accepted":true}""", result.verifierResponseJson)
        assertEquals("wallet://return", result.redirectUrl)
    }

    @Test
    fun presentationResultPreservesFrontChannelResponseArtifacts() {
        val responseUrl = WalletPresentResult(getUrl = "https://verifier.example/callback?error=access_denied")
            .toMobilePresentationResult()
        val formPost = WalletPresentResult(formPostHtml = "<form></form>").toMobilePresentationResult()

        assertEquals(
            MobileWalletPresentationResult.Prepared.OpenUrl(
                "https://verifier.example/callback?error=access_denied"
            ),
            responseUrl,
        )
        assertEquals(MobileWalletPresentationResult.Prepared.SubmitForm("<form></form>"), formPost)
    }

    @Test
    fun presentationResultHonorsExplicitFailedTransmission() {
        val result = WalletPresentResult(
            transmissionSuccess = false,
            verifierResponse = buildJsonObject { put("error", "server_error") },
        ).toMobilePresentationResult()

        assertEquals(
            MobileWalletPresentationResult.Transmitted.Failed("""{"error":"server_error"}"""),
            result,
        )
    }

    @Test
    fun presentationResultRejectsIncompatibleCoreArtifacts() {
        assertFailsWith<IllegalArgumentException> {
            WalletPresentResult(
                getUrl = "https://verifier.example/callback",
                formPostHtml = "<form></form>",
            ).toMobilePresentationResult()
        }
        assertFailsWith<IllegalArgumentException> {
            WalletPresentResult(transmissionSuccess = true).toMobilePresentationResult()
        }
    }

    @Test
    fun credentialsExposeStoredCredentialDataAsJsonString() = runTest {
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "credential-1",
                credential = SdJwtCredential(
                    dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                    credentialData = buildJsonObject {
                        put("given_name", "Ada")
                    },
                    issuer = "https://issuer.example",
                    subject = "did:key:subject",
                    signature = null,
                    signed = null,
                ),
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            keyGenerator = { error("Injected credential listing should not generate a key") },
        )

        val credential = wallet.credentials().single()

        assertEquals("credential-1", credential.id)
        assertEquals("dc+sd-jwt", credential.format)
        assertEquals("https://issuer.example", credential.issuer)
        assertEquals("did:key:subject", credential.subject)
        assertEquals("PID", credential.label)
        assertEquals("""{"given_name":"Ada"}""", credential.credentialDataJson)
    }

    @Test
    fun credentialsExposeResolvedSdJwtClaimsWhenDisclosuresAreAvailable() = runTest {
        val (_, parsedCredential) = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2)
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "credential-sd-jwt",
                credential = parsedCredential,
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            keyGenerator = { error("Injected credential listing should not generate a key") },
        )

        val displayData = displayJson.parseToJsonElement(wallet.credentials().single().credentialDataJson).jsonObject

        assertEquals("Inga", displayData["given_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Silverstone", displayData["family_name"]?.jsonPrimitive?.contentOrNull)
        assertEquals("1991-11-06", displayData["birthdate"]?.jsonPrimitive?.contentOrNull)
        assertFalse(displayData.containsKey("_sd"), "resolved SD-JWT display data should not expose digest commitments as the primary content")
    }

    @Test
    fun presentationPreviewUsesSwiftFriendlyCredentialAndClaimDtos() {
        val preview = MobileWalletPresentationPreview(
            request = MobileWalletPresentationRequestInfo(
                clientId = "https://verifier.example",
                verifierName = "Example Verifier",
                responseUri = "https://verifier.example/direct-post",
                state = "state-1",
                nonce = "nonce-1",
            ),
            credentialOptions = listOf(
                MobileWalletPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "credential-1",
                    multiple = true,
                    format = "vc+sd-jwt",
                    issuer = "https://issuer.example",
                    subject = "did:key:subject",
                    label = "PID",
                    credentialDataJson = """{"given_name":"Ada"}""",
                    disclosures = listOf(
                        MobileWalletPresentationDisclosure(
                            path = "$.given_name",
                            name = "given_name",
                            valueJson = """"Ada"""",
                            displayValue = "Ada",
                            selectivelyDisclosable = true,
                        )
                    ),
                )
            ),
            credentialRequirements = listOf(
                MobileWalletPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )

        assertEquals("https://verifier.example", preview.request.clientId)
        assertEquals("credential-1", preview.credentialOptions.single().credentialId)
        assertEquals(true, preview.credentialOptions.single().multiple)
        assertEquals("Ada", preview.credentialOptions.single().disclosures.single().displayValue)
        assertEquals(listOf(listOf("pid")), preview.credentialRequirements.single().options)
    }

    @Test
    fun digitalCredentialRegistryUsesStableOpaqueMetadataAndExcludesSdJwtInfrastructureClaims() = runTest {
        val registry = RecordingMetadataRegistry()
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "credential-sensitive-local-id",
                credential = SdJwtCredential(
                    dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                    credentialData = buildJsonObject {
                        put("vct", "https://credentials.example/pid")
                        put("iss", "https://issuer.example")
                        put("given_name", "Ada")
                    },
                    issuer = "https://issuer.example",
                    subject = "did:key:subject",
                    signature = null,
                    signed = null,
                ),
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "registry-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            keyGenerator = { error("Registry refresh must not generate keys") },
            credentialRegistry = registry,
        )

        wallet.refreshDigitalCredentialRegistration()
        wallet.refreshDigitalCredentialRegistration()

        assertEquals(2, registry.replacements.size)
        assertEquals(registry.replacements[0], registry.replacements[1])
        val record = registry.replacements.last().second.single()
        assertFalse(record.registryEntryId.contains("credential-sensitive-local-id"))
        assertEquals("https://credentials.example/pid", record.type)
        assertEquals(listOf(listOf("given_name")), record.fields.map { it.path })
        assertFalse(record.fields.any { it.path.singleOrNull() == "iss" })
    }

    @Test
    fun digitalCredentialRegistryKeepsStructuredMdocElementsAtNamespaceElementPaths() = runTest {
        val registry = RecordingMetadataRegistry()
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "mdl-1",
                credential = MdocsCredential(
                    credentialData = buildJsonObject {
                        put("org.iso.18013.5.1", buildJsonObject {
                            put("given_name", "Ada")
                            put("driving_privileges", buildJsonArray {
                                add(buildJsonObject {
                                    put("vehicle_category_code", "B")
                                })
                            })
                        })
                    },
                    signed = null,
                    docType = "org.iso.18013.5.1.mDL",
                ),
                label = "mDL",
            )
        )
        val wallet = MobileWallet(
            walletId = "mdoc-registry-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            keyGenerator = { error("Registry refresh must not generate keys") },
            credentialRegistry = registry,
        )

        wallet.refreshDigitalCredentialRegistration()

        val fields = registry.replacements.single().second.single().fields
        assertEquals(
            listOf(
                listOf("org.iso.18013.5.1", "given_name"),
                listOf("org.iso.18013.5.1", "driving_privileges"),
            ),
            fields.map { it.path },
        )
        assertTrue(fields.all { it.path.size == 2 })
    }

    @Test
    fun annexCParserNormalizesNamespacesAndPreviewRejectsParsedRawMismatchBeforeConsent() = runTest {
        val wallet = MobileWallet(
            walletId = "annex-c-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            keyGenerator = { error("Parsing must not generate keys") },
        )
        val raw = DeviceRequest(
            docType = "org.iso.18013.5.1.mDL",
            requestedElements = mapOf("org.iso.18013.5.1" to listOf("given_name", "family_name")),
        ).encodeToBase64Url()

        val parsed = wallet.parseAnnexCDeviceRequest(raw)

        assertEquals("org.iso.18013.5.1.mDL", parsed.documents.single().docType)
        assertEquals(listOf("family_name", "given_name"), parsed.documents.single().namespaces.values.single())
        assertFailsWith<IllegalArgumentException> {
            wallet.previewAnnexCPresentation(
                MobileWalletAnnexCRequest(
                    parsedRequest = MobileWalletAnnexCParsedRequest(
                        listOf(MobileWalletAnnexCDocumentRequest("eu.europa.ec.eudi.pid.1", emptyMap()))
                    ),
                    verifiedOrigin = "https://verifier.example",
                    deviceRequestBase64Url = raw,
                    encryptionInfoBase64Url = "not-reached",
                )
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun annexCReaderAuthenticationUsesVerifierTranscriptBuildsResponseAndRejectsTampering() = runTest {
        val origin = "https://verifier.example"
        val namespace = "org.iso.18013.5.1"
        val docType = "org.iso.18013.5.1.mDL"
        val readerCertificate = Base64.decode(READER_CERTIFICATE_BASE64)
        // The pre-signed request avoids platform-specific private-key imports; the assertion below prevents fixture drift.
        val signedRequest = DeviceRequest.decodeFromBase64Url(SIGNED_READER_REQUEST)
        val signature = requireNotNull(signedRequest.docRequests.single().readerAuth)
        val readerKey = JWKKey.importFromDerCertificate(readerCertificate).getOrThrow()
        assertTrue(
            signature.verifyDetached(
                readerKey.toCoseVerifier(),
                ReaderAuthenticationPayloads.forDocument(
                    sessionTranscript = AnnexCTranscriptBuilder.buildSessionTranscript(READER_ENCRYPTION_INFO, origin),
                    itemsRequest = signedRequest.docRequests.single().itemsRequest,
                ),
            ),
            "Fixture must use the verifier's exact ISO 18013-7 Annex C transcript",
        )
        val holderSigner = JWKKey.importJWK(HOLDER_TEST_PRIVATE_JWK).getOrThrow()
        val holderKeyId = holderSigner.getKeyId()
        val wallet = MobileWallet(
            walletId = "annex-c-reader-auth-wallet",
            keyStore = PreloadedKeyStore(
                WalletKeyInfo(keyId = holderKeyId, keyType = KeyType.Ed25519.name),
                holderSigner,
            ),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(
                StoredCredential(
                    id = "mdl-1",
                    credential = MdocsCredential(
                        credentialData = buildJsonObject {
                            put(namespace, buildJsonObject { put("given_name", "Ada") })
                        },
                        signed = MdocsExamples.mdocsExampleBase64Url,
                        docType = docType,
                    ),
                    label = "mDL",
                )
            ),
            keyGenerator = { error("Reader-authentication preview must not generate keys") },
            readerTrustEvaluator = MobileWalletReaderTrustEvaluator { chain ->
                assertEquals(1, chain.size)
                assertContentEquals(readerCertificate, chain.single())
                MobileWalletReaderTrust.Trusted("CN=Example")
            },
        )
        val parsedRequest = wallet.parseAnnexCDeviceRequest(signedRequest.encodeToBase64Url())

        val preview = wallet.previewAnnexCPresentation(
            MobileWalletAnnexCRequest(
                parsedRequest = parsedRequest,
                verifiedOrigin = origin,
                deviceRequestBase64Url = SIGNED_READER_REQUEST,
                encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
            )
        )

        assertEquals(MobileWalletReaderTrust.Trusted("CN=Example"), preview.readerTrust)
        val response = wallet.submitAnnexCPresentation(
            MobileWalletAnnexCSubmission(
                requestId = preview.requestId,
                verifiedOrigin = origin,
                deviceRequestBase64Url = SIGNED_READER_REQUEST,
                encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
                selectedCredentialOptions = preview.credentialOptions.map {
                    MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId)
                },
            )
        )
        assertEquals(MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C, response.protocol)
        assertTrue(
            displayJson.parseToJsonElement(response.dataJson).jsonObject["response"]
                ?.jsonPrimitive?.content?.isNotBlank() == true
        )

        val tamperedSignature = signature.copy(
            signature = signature.signature.copyOf().also { bytes ->
                bytes[0] = (bytes[0].toInt() xor 1).toByte()
            }
        )
        val tamperedRequest = signedRequest.copy(
            docRequests = listOf(signedRequest.docRequests.single().copy(readerAuth = tamperedSignature)),
        )
        assertFailsWith<IllegalArgumentException> {
            wallet.previewAnnexCPresentation(
                MobileWalletAnnexCRequest(
                    parsedRequest = parsedRequest,
                    verifiedOrigin = origin,
                    deviceRequestBase64Url = tamperedRequest.encodeToBase64Url(),
                    encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
                )
            )
        }
    }

    @Test
    fun capabilityAndMigrationModelsFailClosedByDefault() = runTest {
        assertFalse(UnavailableMobileWalletCredentialRegistry.capabilities.platformAvailable)
        assertFalse(UnavailableMobileWalletCredentialRegistry.capabilities.registrationAvailable)
        assertIs<MobileWalletReaderTrust.Unverified>(
            UnconfiguredMobileWalletReaderTrustEvaluator.evaluate(emptyList())
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mobileWalletEventStreamDoesNotBackpressureSlowCollectors() = runTest {
        val stream = MobileWalletEventStream(replay = 1, extraBufferCapacity = 1)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            stream.events.collect {
                delay(Long.MAX_VALUE)
            }
        }

        runCurrent()

        repeat(100) { index ->
            val emitted = stream.tryEmit(progressEvent("issuance_progress_$index"))

            assertTrue(emitted, "Progress event $index should not suspend or fail when the buffer is full")
        }

        collector.cancel()
    }

    private fun progressEvent(name: String) = MobileWalletEvent(
        name = name,
        phase = MobileWalletEventPhase.issuance,
        status = MobileWalletEventStatus.progress,
    )

    private val displayJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private companion object {
        const val READER_CERTIFICATE_BASE64 =
            "MIIBsTCCAVegAwIBAgIUJklaRrIjkEZlDdPk2+qPneHHD6kwCgYIKoZIzj0EAwIwLjEQMA4GA1UEAwwHRXhhbXBsZTENMAsGA1UECgwEVGVzdDELMAkGA1UEBhMCVVMwHhcNMjYwMzMxMDkwNDMwWhcNMjcwMzMxMDkwNDMwWjAuMRAwDgYDVQQDDAdFeGFtcGxlMQ0wCwYDVQQKDARUZXN0MQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABM4ukI9BoHfMYjKmokWc5GMiN7DJBQAPPZBHXHhwmuQE+JyeRcamM+uCS1N+naE0itVbs7fQ/5xbujSSK9pYdb6jUzBRMB0GA1UdDgQWBBSwjqgulcWH4AqTJwPjBGj3VGIAsTAfBgNVHSMEGDAWgBSwjqgulcWH4AqTJwPjBGj3VGIAsTAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0gAMEUCIQCoWAleGRqR+kb+5SeRt/scogZPiQiM7wJ69tadEPPJwQIgdygIZSMQSXlxXbZ10QKtN6qSjggqFVUV4/Z2/pnBUBk="
        const val READER_ENCRYPTION_INFO =
            "gmVkY2FwaaJlbm9uY2VQAQIDBAUGBwgJCgsMDQ4PEHJyZWNpcGllbnRQdWJsaWNLZXmkAQIgASFYIM4ukI9BoHfMYjKmokWc5GMiN7DJBQAPPZBHXHhwmuQEIlgg-JyeRcamM-uCS1N-naE0itVbs7fQ_5xbujSSK9pYdb4"
        const val SIGNED_READER_REQUEST =
            "omd2ZXJzaW9uYzEuMGtkb2NSZXF1ZXN0c4GibGl0ZW1zUmVxdWVzdNgYWEqiZ2RvY1R5cGV1b3JnLmlzby4xODAxMy41LjEubURMam5hbWVTcGFjZXOhcW9yZy5pc28uMTgwMTMuNS4xoWpnaXZlbl9uYW1l9GpyZWFkZXJBdXRohEOhASahGCFZAbUwggGxMIIBV6ADAgECAhQmSVpGsiOQRmUN0-Tb6o-d4ccPqTAKBggqhkjOPQQDAjAuMRAwDgYDVQQDDAdFeGFtcGxlMQ0wCwYDVQQKDARUZXN0MQswCQYDVQQGEwJVUzAeFw0yNjAzMzEwOTA0MzBaFw0yNzAzMzEwOTA0MzBaMC4xEDAOBgNVBAMMB0V4YW1wbGUxDTALBgNVBAoMBFRlc3QxCzAJBgNVBAYTAlVTMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzi6Qj0Ggd8xiMqaiRZzkYyI3sMkFAA89kEdceHCa5AT4nJ5FxqYz64JLU36doTSK1Vuzt9D_nFu6NJIr2lh1vqNTMFEwHQYDVR0OBBYEFLCOqC6VxYfgCpMnA-MEaPdUYgCxMB8GA1UdIwQYMBaAFLCOqC6VxYfgCpMnA-MEaPdUYgCxMA8GA1UdEwEB_wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIhAKhYCV4ZGpH6Rv7lJ5G3-xyiBk-JCIzvAnr21p0Q88nBAiB3KAhlIxBJeXFdtnXRAq03qpKOCCoVVRXj9nb-mcFQGfZYQE524YTazDQiCCYBcZRzZHc0GfcMBDJVNIRZ1Svd3hXLG7pj8eTefRllnxRtj4nGQO-MQIJoRqPaDiMuIh1BLVU"
        const val HOLDER_TEST_PRIVATE_JWK =
            """{"kty":"OKP","crv":"Ed25519","kid":"holder-key","x":"LRHvL7I9utgSl47JksY0-uY21TlIxp_queROJJzknNM","d":"lPR4XjW-9_rI4hLjvdjmjoGC6ozblm9juDv4OHYdm5M"}"""
    }

    private class RecordingDatabaseKeyProvider : DatabaseEncryptionKeyProvider {
        override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey =
            DatabaseEncryptionKey("$walletId:$databaseName", ByteArray(32))

        override suspend fun deleteKey(walletId: String, databaseName: String) = Unit
    }

    private class RecordingMetadataRegistry : MobileWalletCredentialRegistry {
        val replacements = mutableListOf<Pair<String, List<MobileWalletCredentialRegistryRecord>>>()
        override val capabilities = UnavailableMobileWalletCredentialRegistry.capabilities

        override suspend fun replace(
            registryId: String,
            records: List<MobileWalletCredentialRegistryRecord>,
        ): MobileWalletCredentialRegistrationResult {
            replacements += registryId to records
            return MobileWalletCredentialRegistrationResult(true, records.size)
        }
    }

    private class PreloadedKeyStore(
        private val keyInfo: WalletKeyInfo,
        private val key: Key? = null,
    ) : WalletKeyStore {
        var listKeysCalls = 0
        val removedKeyIds = mutableListOf<String>()

        override suspend fun getKey(keyId: String) = key?.takeIf { keyId == keyInfo.keyId }

        override suspend fun listKeys(): Flow<WalletKeyInfo> {
            listKeysCalls++
            return listOf(keyInfo).asFlow()
        }

        override suspend fun addKey(key: id.walt.crypto.keys.Key): String =
            error("Preloaded test key store should not add keys")

        override suspend fun removeKey(keyId: String): Boolean {
            removedKeyIds += keyId
            return true
        }
    }

    private class PreloadedDidStore(private val did: WalletDidEntry) : WalletDidStore {
        var listDidsCalls = 0
        val removedDids = mutableListOf<String>()

        override suspend fun getDid(did: String): WalletDidEntry? = this.did.takeIf { it.did == did }

        override suspend fun listDids(): Flow<WalletDidEntry> {
            listDidsCalls++
            return listOf(did).asFlow()
        }

        override suspend fun addDid(entry: WalletDidEntry) =
            error("Preloaded test DID store should not add DIDs")

        override suspend fun removeDid(did: String): Boolean {
            removedDids += did
            return true
        }
    }

    private class RecordingCredentialStore(
        private vararg val credentials: StoredCredential,
    ) : WalletCredentialStore {
        val removedCredentialIds = mutableListOf<String>()

        override suspend fun getCredential(id: String): StoredCredential? = credentials.firstOrNull { it.id == id }

        override suspend fun listCredentials(): Flow<StoredCredential> =
            credentials.toList().asFlow()

        override suspend fun addCredential(entry: StoredCredential) =
            error("Recording credential store should not add credentials in this test")

        override suspend fun removeCredential(id: String): Boolean {
            removedCredentialIds += id
            return true
        }
    }
}
