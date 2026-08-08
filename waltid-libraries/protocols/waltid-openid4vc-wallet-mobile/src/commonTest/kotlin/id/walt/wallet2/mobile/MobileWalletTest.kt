@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseVerifier
import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.MdocsExamples
import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import id.walt.wallet2.handlers.WalletIssuanceSessionStore
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.http.URLBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import id.walt.crypto2.keys.Key as ManagedKeyMaterial

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
        val (walletId, defaultKeyType, attestationConfig, persistence, onEvent, preferredLocales, transactionDataProfiles) = config

        assertEquals("default", walletId)
        assertEquals(MobileWalletKeyType.secp256r1, defaultKeyType)
        assertEquals(null, attestationConfig)
        assertEquals(MobileWalletPersistence(), persistence)
        assertEquals(emptyList(), preferredLocales)
        assertEquals(emptyList(), transactionDataProfiles)
        assertSame(config.onEvent, onEvent)
        assertIs<MobileWalletDatabaseKey.Managed>(config.persistence.databaseKey)
        assertEquals(null, config.persistence.credentialStore)
        assertEquals(null, config.persistence.didStore)
    }

    @Test
    fun defaultTrustConfigurationRejectsUnknownPreRegisteredVerifier() = runTest {
        val verifierKey = JWKKey.generate(KeyType.Ed25519)
        val requestUrl = preRegisteredRequestUrl(verifierKey)
        val wallet = MobileWallet(
            walletId = "default-trust-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "unused-key", keyType = "Ed25519")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:unused", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = unusedKeyGenerator(),
        )

        val failure = assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            wallet.previewPresentation(requestUrl)
        }

        assertEquals(ClientIdError.PreRegisteredClientNotFound("verifier2"), failure.clientIdError)
    }

    @Test
    fun explicitTrustConfigurationAuthenticatesPreRegisteredVerifier() = runTest {
        val verifierKey = JWKKey.generate(KeyType.Ed25519)
        val requestUrl = preRegisteredRequestUrl(verifierKey)
        val wallet = walletWithTrust(
            ClientIdTrustConfiguration(
                preRegisteredClients = mapOf(
                    "verifier2" to ClientMetadata(
                        jwks = ClientMetadata.Jwks(listOf(verifierKey.getPublicKey().exportJWKObject())),
                    )
                ),
            )
        )

        val preview = assertIs<MobileWalletPresentationPreviewResult.Invalid>(
            wallet.previewPresentation(requestUrl)
        )

        assertEquals("verifier2", preview.request.clientId)
        assertEquals(MobileWalletPresentationErrorCode.invalidRequest, preview.errorCode)
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
    fun persistenceCanCombineProvidedDatabaseKeyWithCredentialAndDidStoreOverrides() {
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val databaseKeyProvider = RecordingDatabaseKeyProvider()

        val persistence = MobileWalletPersistence(
            databaseKey = MobileWalletDatabaseKey.Provided(databaseKeyProvider),
            credentialStore = credentialStore,
            didStore = didStore,
        )

        assertSame(databaseKeyProvider, assertIs<MobileWalletDatabaseKey.Provided>(persistence.databaseKey).provider)
        assertSame(credentialStore, persistence.credentialStore)
        assertSame(didStore, persistence.didStore)
    }

    @Test
    fun persistedManagedKeyIsRestoredWithoutLegacyKeyAfterRestart() = runTest {
        val managedKey = object : ManagedKeyMaterial {
            override val id = KeyId("managed-key")
            override val spec = KeySpec.Ec(EcCurve.P256)
            override val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        }
        val keyStore = PreloadedKeyStore(
            keyInfo = WalletKeyInfo(keyId = managedKey.id.value, keyType = "secp256r1"),
            managedKey = managedKey,
            failIfLegacyKeyRequested = true,
        )
        val wallet = MobileWallet(
            walletId = "managed-key-wallet",
            keyStore = keyStore,
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:managed", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = unusedKeyGenerator(),
        )

        val bootstrap = wallet.bootstrap()

        assertEquals(managedKey.id.value, bootstrap.keyId)
        assertEquals("did:key:managed", bootstrap.did)
        assertEquals(1, keyStore.managedKeyLookupCalls)
    }

    @Test
    fun persistedDidWithMissingPlatformKeyFailsBootstrap() = runTest {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "missing-key", keyType = "secp256r1"))
        val wallet = MobileWallet(
            walletId = "missing-key-wallet",
            keyStore = keyStore,
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:missing", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = unusedKeyGenerator(),
        )

        val failure = assertFailsWith<IllegalArgumentException> { wallet.bootstrap() }

        assertTrue("missing-key" in failure.message.orEmpty())
    }

    @Test
    fun deleteWalletRemovesEntriesFromActiveStores() = runTest {
        val keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1"))
        val didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap())))
        val credentialStore = RecordingCredentialStore()
        val issuanceSessionStore = RecordingIssuanceSessionStore(
            WalletIssuanceSessionRecord(
                id = "active:session-1",
                sessionId = "session-1",
                kind = WalletIssuanceSessionRecordKind.ACTIVE_SESSION,
                payload = "<encrypted>",
                updatedAtEpochMilliseconds = 1L,
            ),
            WalletIssuanceSessionRecord(
                id = "deferred:credential-1",
                sessionId = "session-1",
                kind = WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL,
                payload = "<encrypted>",
                updatedAtEpochMilliseconds = 2L,
            ),
        )
        val wallet = MobileWallet(
            walletId = "custom-wallet",
            keyStore = keyStore,
            didStore = didStore,
            credentialStore = credentialStore,
            issuanceSessionStore = issuanceSessionStore,
            generateAndPersistKey = unusedKeyGenerator(),
        )

        wallet.deleteWallet()

        assertEquals(listOf("custom-key"), keyStore.removedKeyIds)
        assertEquals(listOf("did:key:custom"), didStore.removedDids)
        assertEquals(emptyList(), credentialStore.removedCredentialIds)
        assertTrue(issuanceSessionStore.records.isEmpty())
    }

    @Test
    fun mobileWalletKeyTypeMapsToInternalKeySpec() {
        assertEquals(KeySpec.Edwards(EdwardsCurve.ED25519), MobileWalletKeyType.Ed25519.toKeySpec())
        assertEquals(KeySpec.Ec(EcCurve.SECP256K1), MobileWalletKeyType.secp256k1.toKeySpec())
        assertEquals(KeySpec.Ec(EcCurve.P256), MobileWalletKeyType.secp256r1.toKeySpec())
        assertEquals(KeySpec.Ec(EcCurve.P384), MobileWalletKeyType.secp384r1.toKeySpec())
        assertEquals(KeySpec.Ec(EcCurve.P521), MobileWalletKeyType.secp521r1.toKeySpec())
        assertEquals(KeySpec.Rsa(2048), MobileWalletKeyType.RSA.toKeySpec())
        assertEquals(KeySpec.Rsa(3072), MobileWalletKeyType.RSA3072.toKeySpec())
        assertEquals(KeySpec.Rsa(4096), MobileWalletKeyType.RSA4096.toKeySpec())
    }

    @Test
    fun walletSessionEventsMapExhaustivelyToMobileWalletEvents() {
        assertEquals(WalletSessionEvent.entries.size, MobileWalletEvent.entries.size)
        WalletSessionEvent.entries.forEach { event ->
            assertEquals(event.name, event.toMobileWalletEvent().name)
        }

        assertEquals(MobileWalletEventPhase.issuance, MobileWalletEvent.issuance_offer_resolved.phase)
        assertEquals(MobileWalletEventStatus.progress, MobileWalletEvent.issuance_offer_resolved.status)
        assertEquals(MobileWalletEventPhase.presentation, MobileWalletEvent.presentation_completed.phase)
        assertEquals(MobileWalletEventStatus.completed, MobileWalletEvent.presentation_completed.status)
        assertEquals(MobileWalletEventStatus.failed, MobileWalletEvent.issuance_failed.status)
    }

    @Test
    fun presentationCredentialRequirementsRejectEmptyCombinations() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(listOf(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialRequirement(listOf(listOf(" ")))
        }
    }

    @Test
    fun presentationOutputModelsRejectMissingCredentialQueryIds() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationCredentialOption(
                queryId = " ",
                credentialId = "credential-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = null,
                credentialDataJson = "{}",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            presentationTransactionData(credentialQueryIds = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            presentationTransactionData(credentialQueryIds = listOf(" "))
        }
    }

    @Test
    fun presentationRequestInfoRequiresClientIdAndNonce() {
        assertFailsWith<IllegalArgumentException> {
            presentationRequestInfo(clientId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            presentationRequestInfo(nonce = " ")
        }
    }

    @Test
    fun presentationRequestContextRequiresClientIdButAllowsMissingNonce() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletPresentationRequestContext(
                clientId = " ",
                verifierMetadata = null,
                responseUri = null,
                state = null,
                nonce = null,
                responseEncryption = MobileWalletResponseEncryption.NotRequired,
            )
        }

        val context = MobileWalletPresentationRequestContext(
            clientId = "https://verifier.example",
            verifierMetadata = null,
            responseUri = null,
            state = null,
            nonce = null,
            responseEncryption = MobileWalletResponseEncryption.NotRequired,
        )
        assertEquals("https://verifier.example", context.clientId)
        assertEquals(null, context.nonce)
    }

    @Test
    fun presentationDisclosuresRejectImpossibleSelectableStates() {
        assertFailsWith<IllegalArgumentException> {
            presentationDisclosure(selectivelyDisclosable = false, required = false, selectable = true)
        }
        assertFailsWith<IllegalArgumentException> {
            presentationDisclosure(selectivelyDisclosable = true, required = true, selectable = true)
        }
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
            generateAndPersistKey = unusedKeyGenerator(),
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
            generateAndPersistKey = unusedKeyGenerator(),
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
            previewHandle = MobileWalletPresentationPreviewHandle("preview-1"),
            request = MobileWalletPresentationRequestInfo(
                clientId = "https://verifier.example",
                verifierMetadata = MobileWalletVerifierMetadata(
                    display = MobileWalletMetadataDisplay(
                        name = "Example Verifier",
                        locale = "en",
                        logoUri = null,
                        logoAltText = null,
                    ),
                    clientUri = "https://verifier.example",
                    policyUri = null,
                    termsOfServiceUri = null,
                ),
                responseUri = "https://verifier.example/direct-post",
                state = "state-1",
                nonce = "nonce-1",
                responseEncryption = MobileWalletResponseEncryption.NotRequired,
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
            generateAndPersistKey = { error("Registry refresh must not generate keys") },
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
        assertFalse(record.fields.single().selectivelyDisclosable)
    }

    @Test
    fun digitalCredentialRegistryMarksOnlySdJwtDisclosureLocationsAsSelectable() = runTest {
        val registry = RecordingMetadataRegistry()
        val (_, credential) = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2)
        val sdJwt = assertIs<SdJwtCredential>(credential)
        val selectivelyDisclosablePaths = requireNotNull(sdJwt.disclosures)
            .mapNotNull { disclosure ->
                disclosure.location?.mapNotNull { component -> component.jsonPrimitive.contentOrNull }
            }
            .toSet()
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "pid-1",
                credential = credential,
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "registry-disclosures-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            generateAndPersistKey = { error("Registry refresh must not generate keys") },
            credentialRegistry = registry,
        )

        wallet.refreshDigitalCredentialRegistration()

        val fields = registry.replacements.single().second.single().fields
        assertTrue(fields.any { it.selectivelyDisclosable })
        fields.forEach { field ->
            assertEquals(field.path in selectivelyDisclosablePaths, field.selectivelyDisclosable)
        }
        // Every claim this fixture carries beyond the infrastructure claims is disclosable, so the
        // presence of a non-disclosable field would mean an infrastructure claim leaked through.
        assertEquals(
            setOf(listOf("sub"), listOf("given_name"), listOf("family_name"), listOf("birthdate")),
            fields.map { it.path }.toSet(),
        )
    }

    @Test
    fun digitalCredentialRegistryExcludesSdJwtHashAlgorithmDeclaration() = runTest {
        val registry = RecordingMetadataRegistry()
        val (_, credential) = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2)
        // Disclosure resolution strips `_sd` but not its sibling `_sd_alg`, so the raw claims still
        // carry it and the registry has to exclude it explicitly.
        assertTrue(assertIs<SdJwtCredential>(credential).credentialData.containsKey("_sd_alg"))
        val wallet = MobileWallet(
            walletId = "registry-sd-alg-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(
                StoredCredential(id = "pid-1", credential = credential, label = "PID"),
            ),
            generateAndPersistKey = { error("Registry refresh must not generate keys") },
            credentialRegistry = registry,
        )

        wallet.refreshDigitalCredentialRegistration()

        val fields = registry.replacements.single().second.single().fields
        assertFalse(fields.any { "_sd_alg" in it.path || "_sd" in it.path })
    }

    @Test
    fun digitalCredentialRegistryMarksArrayDisclosuresAtTheirContainingClaimPath() = runTest {
        val registry = RecordingMetadataRegistry()
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "pid-1",
                credential = SdJwtCredential(
                    dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                    credentialData = buildJsonObject {
                        put("vct", "https://credentials.example/pid")
                        put("given_name", "Ada")
                        put("nationalities", buildJsonArray { add(JsonPrimitive("AT")) })
                    },
                    disclosures = listOf(
                        SdJwtSelectiveDisclosure(
                            salt = "c2FsdA",
                            name = null,
                            value = JsonPrimitive("AT"),
                            location = listOf(JsonPrimitive("nationalities"), JsonPrimitive(0)),
                        ),
                    ),
                    // A disclosable array element is carried as `{"...": digest}` inside the array
                    // rather than in an `_sd` array, so no `disclosables` entry describes it.
                    disclosables = emptyMap(),
                    originalCredentialData = buildJsonObject {
                        put("vct", "https://credentials.example/pid")
                        put("given_name", "Ada")
                        put("nationalities", buildJsonArray {
                            add(buildJsonObject { put("...", "kGVzdGRpZ2VzdA") })
                        })
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
            walletId = "registry-array-disclosure-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            generateAndPersistKey = { error("Registry refresh must not generate keys") },
            credentialRegistry = registry,
        )

        wallet.refreshDigitalCredentialRegistration()

        // The array is registered as one leaf, so an index-addressed disclosure must mark that leaf
        // rather than a shortened path that matches nothing.
        val fields = registry.replacements.single().second.single().fields
        assertEquals(
            mapOf(listOf("given_name") to false, listOf("nationalities") to true),
            fields.associate { it.path to it.selectivelyDisclosable },
        )
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
            generateAndPersistKey = { error("Registry refresh must not generate keys") },
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
    fun failedRegistrySynchronizationIsReportedWithoutFailingTheCommittedWalletOperation() = runTest {
        val registry = FailingMetadataRegistry(IllegalStateException("Credential Manager rejected the registry"))
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "pid-1",
                credential = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2).second,
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "registry-failure-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            generateAndPersistKey = { error("Registry failure must not generate keys") },
            credentialRegistry = registry,
        )

        // The store removed the credential, so the deletion stands even though the projection of it did not.
        assertTrue(wallet.deleteCredential("pid-1"))
        assertEquals(listOf("pid-1"), credentialStore.removedCredentialIds)
        val reported = assertNotNull(wallet.digitalCredentialRegistration.value)
        assertFalse(reported.available)
        assertEquals("Credential Manager rejected the registry", reported.reason)

        // Retrying re-publishes current wallet state rather than requiring another wallet operation.
        val retried = wallet.refreshDigitalCredentialRegistration()
        assertFalse(retried.available)
        assertEquals(2, registry.replaceCalls)
    }

    @Test
    fun unavailableRegistrySurfacesItsReasonWithoutThrowing() = runTest {
        val registry = FailingMetadataRegistry()
        val wallet = MobileWallet(
            walletId = "registry-unavailable-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = { error("Registry refresh must not generate keys") },
            credentialRegistry = registry,
        )

        val result = wallet.refreshDigitalCredentialRegistration()

        assertFalse(result.available)
        assertEquals("Registry is unavailable", result.reason)
        assertEquals(result, wallet.digitalCredentialRegistration.value)
    }

    @Test
    fun annexCParserNormalizesNamespacesAndPreviewRejectsParsedRawMismatchBeforeConsent() = runTest {
        val wallet = MobileWallet(
            walletId = "annex-c-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = { error("Parsing must not generate keys") },
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
        // A managed crypto2 key, not an imported private JWK: Android's software provider only
        // accepts RSA private JWK material, so importing this Ed25519 key would fail there.
        val holderSigner = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder-key"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val holderKeyId = holderSigner.id.value
        val wallet = MobileWallet(
            walletId = "annex-c-reader-auth-wallet",
            keyStore = PreloadedKeyStore(
                WalletKeyInfo(keyId = holderKeyId, keyType = "Ed25519"),
                managedKey = holderSigner,
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
            generateAndPersistKey = { error("Reader-authentication preview must not generate keys") },
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
        val submission = MobileWalletAnnexCSubmission(
            requestId = preview.requestId,
            verifiedOrigin = origin,
            deviceRequestBase64Url = SIGNED_READER_REQUEST,
            encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
            selectedCredentialOptions = preview.credentialOptions.map {
                MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId)
            },
        )
        assertFailsWith<IllegalArgumentException> {
            wallet.submitAnnexCPresentation(submission.copy(verifiedOrigin = "https://other.example"))
        }
        val response = wallet.submitAnnexCPresentation(
            submission
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

    /**
     * The four non-trusted reader states must stay distinguishable, and none of them may be produced
     * by a signature that failed to verify.
     *
     * The last case is the one that matters most: on Apple's deferred path the preview cannot check
     * the signature at all, so consent is granted while the reader is still unauthenticated. A bad
     * signature arriving with the raw request must reject the submission rather than be reported as
     * a trust state the user already accepted.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun annexCDistinguishesReaderTrustStatesAndRejectsBadSignaturesAfterConsent() = runTest {
        val origin = "https://verifier.example"
        val namespace = "org.iso.18013.5.1"
        val docType = "org.iso.18013.5.1.mDL"
        val signedRequest = DeviceRequest.decodeFromBase64Url(SIGNED_READER_REQUEST)
        val signature = requireNotNull(signedRequest.docRequests.single().readerAuth)
        val holderSigner = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder-key"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        // No readerTrustEvaluator: this exercises the default policy, which must never report Trusted.
        val wallet = MobileWallet(
            walletId = "annex-c-reader-trust-states-wallet",
            keyStore = PreloadedKeyStore(
                WalletKeyInfo(keyId = holderSigner.id.value, keyType = "Ed25519"),
                managedKey = holderSigner,
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
            generateAndPersistKey = { error("Reader-trust previews must not generate keys") },
        )
        val parsedRequest = wallet.parseAnnexCDeviceRequest(signedRequest.encodeToBase64Url())

        // A verified signature with no configured trust policy: untrusted, and truthfully so.
        val verified = wallet.previewAnnexCPresentation(
            MobileWalletAnnexCRequest(
                parsedRequest = parsedRequest,
                verifiedOrigin = origin,
                deviceRequestBase64Url = SIGNED_READER_REQUEST,
                encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
            )
        )
        val untrusted = assertIs<MobileWalletReaderTrust.Untrusted>(verified.readerTrust)
        assertTrue(
            untrusted.reason.contains("no reader trust policy is configured"),
            "The default evaluator must say why the reader is untrusted, not imply a rejected policy: " +
                untrusted.reason,
        )

        // A request carrying no reader authentication at all: anonymous, not merely unverified.
        val unauthenticated = DeviceRequest(
            docType = docType,
            requestedElements = mapOf(namespace to listOf("given_name")),
        )
        assertEquals(
            MobileWalletReaderTrust.NotAuthenticated,
            wallet.previewAnnexCPresentation(
                MobileWalletAnnexCRequest(
                    parsedRequest = wallet.parseAnnexCDeviceRequest(unauthenticated.encodeToBase64Url()),
                    verifiedOrigin = origin,
                    deviceRequestBase64Url = unauthenticated.encodeToBase64Url(),
                    encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
                )
            ).readerTrust,
        )

        // Apple's pre-consent shape: the raw request is withheld, so nothing has been checked yet.
        val deferred = wallet.previewAnnexCPresentation(
            MobileWalletAnnexCRequest(parsedRequest = parsedRequest, verifiedOrigin = origin)
        )
        assertEquals(MobileWalletReaderTrust.PendingRawRequest, deferred.readerTrust)

        val tamperedRequest = signedRequest.copy(
            docRequests = listOf(
                signedRequest.docRequests.single().copy(
                    readerAuth = signature.copy(
                        signature = signature.signature.copyOf().also { bytes ->
                            bytes[0] = (bytes[0].toInt() xor 1).toByte()
                        }
                    )
                )
            ),
        )
        val rejection = assertFailsWith<IllegalArgumentException> {
            wallet.submitAnnexCPresentation(
                MobileWalletAnnexCSubmission(
                    requestId = deferred.requestId,
                    verifiedOrigin = origin,
                    deviceRequestBase64Url = tamperedRequest.encodeToBase64Url(),
                    encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
                    selectedCredentialOptions = deferred.credentialOptions.map {
                        MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId)
                    },
                )
            )
        }
        // The submission must fail on the signature, not on some earlier structural check that would
        // make this test pass without proving anything about reader authentication.
        assertTrue(
            rejection.message?.contains("signature") == true,
            "Expected a reader-authentication signature rejection, got: ${rejection.message}",
        )
    }

    /**
     * A recipient key the response could never be HPKE-sealed to must stop the request before the
     * consent dialog, not at submission.
     *
     * Getting this wrong is not merely an ordering nit: a wallet that discovers the unusable key only
     * while sealing has already shown the user which claims a reader asked for and obtained their
     * approval to disclose them, and then fails. So the assertions below pin *both* that the request
     * is rejected and that no credential was ever read - a store access is the observable proxy for
     * "the wallet started preparing a consent screen".
     */
    @Test
    fun annexCRejectsUnsealableHpkeRecipientKeyBeforeConsent() = runTest {
        val docType = "org.iso.18013.5.1.mDL"
        val namespace = "org.iso.18013.5.1"
        val credentialStore = RecordingCredentialStore(
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
        )
        val wallet = MobileWallet(
            walletId = "annex-c-hpke-validation-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "unused-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:unused", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            generateAndPersistKey = unusedKeyGenerator(),
        )
        val request = DeviceRequest(
            docType = docType,
            requestedElements = mapOf(namespace to listOf("given_name")),
        ).encodeToBase64Url()
        val parsedRequest = wallet.parseAnnexCDeviceRequest(request)

        // A structurally valid encryptionInfo whose recipient key the Annex C HPKE suite cannot use.
        // The nonce is deliberately well-formed: DCAPIEncryptionParameters enforces its own 16-byte
        // minimum, so a short nonce would make this test pass on the wrong check.
        val wrongCurveKey = DCAPIEncryptionInfo(
            nonce = ByteArray(16) { it.toByte() },
            recipientPublicKey = CoseKey(
                kty = Cose.KeyTypes.EC2,
                crv = Cose.EllipticCurves.P_384,
                x = ByteArray(48) { 1 },
                y = ByteArray(48) { 2 },
            ),
        ).encodeToBase64Url()
        val privateKeyIncluded = DCAPIEncryptionInfo(
            nonce = ByteArray(16) { it.toByte() },
            recipientPublicKey = coseCompliantCbor.decodeFromByteArray(
                DCAPIEncryptionInfo.serializer(),
                READER_ENCRYPTION_INFO.decodeBase64Url(),
            ).encryptionParameters.recipientPublicKey.copy(d = ByteArray(32) { 3 }),
        ).encodeToBase64Url()

        listOf(
            wrongCurveKey to "P-256",
            privateKeyIncluded to "public material only",
        ).forEach { (encryptionInfo, expectedReason) ->
            val rejection = assertFailsWith<IllegalArgumentException> {
                wallet.previewAnnexCPresentation(
                    MobileWalletAnnexCRequest(
                        parsedRequest = parsedRequest,
                        verifiedOrigin = "https://verifier.example",
                        deviceRequestBase64Url = request,
                        encryptionInfoBase64Url = encryptionInfo,
                    )
                )
            }
            assertTrue(
                rejection.causeChainMessages().any { it.contains(expectedReason) },
                "Expected an HPKE recipient-key rejection mentioning '$expectedReason', got: " +
                    rejection.causeChainMessages(),
            )
        }
        assertTrue(
            credentialStore.streamCount == 0,
            "The wallet read credentials for a request whose encryption metadata is unusable",
        )
    }

    /**
     * Reader authentication must be restricted to the algorithms ISO 18013-5 §9.1.3.4 permits, and the
     * restriction has to be read from the *protected* header where it is signed over.
     *
     * `ESP256` is the sharp case: it is a legitimate, fully-specified P-256 ECDSA identifier that the
     * allowlist deliberately excludes, so a wallet that trusted `alg` blindly would happily verify it.
     * The assertion walks the cause chain because both call sites wrap the failure - matching only on
     * the outer message would also pass for an ordinary bad signature and prove nothing.
     */
    @Test
    fun annexCRejectsReaderAuthenticationAlgorithmOutsideTheAllowlist() = runTest {
        val wallet = annexCWalletWithMdl("annex-c-reader-alg-allowlist-wallet")
        val signedRequest = DeviceRequest.decodeFromBase64Url(SIGNED_READER_REQUEST)
        val docRequest = signedRequest.docRequests.single()
        val readerAuth = requireNotNull(docRequest.readerAuth)
        val protectedHeaders = coseCompliantCbor.decodeFromByteArray(
            CoseHeaders.serializer(),
            readerAuth.protected,
        )
        assertEquals(Cose.Algorithm.ES256, protectedHeaders.algorithm, "Fixture must be an ES256 signature")
        val disallowedAlgorithm = signedRequest.copy(
            docRequests = listOf(
                docRequest.copy(
                    readerAuth = readerAuth.copy(
                        protected = coseCompliantCbor.encodeToByteArray(
                            CoseHeaders.serializer(),
                            protectedHeaders.copy(algorithm = Cose.Algorithm.ESP256),
                        ),
                    ),
                )
            ),
        )
        val parsedRequest = wallet.parseAnnexCDeviceRequest(signedRequest.encodeToBase64Url())

        val rejection = assertFailsWith<IllegalArgumentException> {
            wallet.previewAnnexCPresentation(
                MobileWalletAnnexCRequest(
                    parsedRequest = parsedRequest,
                    verifiedOrigin = "https://verifier.example",
                    deviceRequestBase64Url = disallowedAlgorithm.encodeToBase64Url(),
                    encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
                )
            )
        }
        assertTrue(
            rejection.causeChainMessages().any { it.contains("COSE algorithm is not allowed") },
            "Expected rejection by the reader-authentication algorithm allowlist, got: " +
                rejection.causeChainMessages(),
        )
    }

    /**
     * Every reader-authentication signature in one request must come from the same certificate chain.
     *
     * Without this, a request could pair a signature the wallet can verify with a second signature
     * from an unrelated chain, and whichever chain reached [MobileWalletReaderTrustEvaluator] would
     * decide the trust state the user is shown - so the reader identity displayed at consent need not
     * be the one that authenticated the request. The mismatch is checked before signature
     * verification, which is why the second signature here can be arbitrary bytes.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun annexCRejectsReaderAuthenticationSignaturesFromDifferentCertificateChains() = runTest {
        val wallet = annexCWalletWithMdl("annex-c-reader-chain-mismatch-wallet")
        val signedRequest = DeviceRequest.decodeFromBase64Url(SIGNED_READER_REQUEST)
        val docRequest = signedRequest.docRequests.single()
        val readerAuth = requireNotNull(docRequest.readerAuth)
        // Only the *second* signature can trip the check: the first one establishes the chain the rest
        // are compared against, so a single-signature request could never exercise this.
        val mismatchedChains = signedRequest.copy(
            docRequests = listOf(
                docRequest,
                docRequest.copy(
                    readerAuth = readerAuth.copy(
                        unprotected = readerAuth.unprotected.copy(
                            x5chain = listOf(CoseCertificate(Base64.decode(OTHER_READER_CERTIFICATE_BASE64))),
                        ),
                        protected = coseCompliantCbor.encodeToByteArray(
                            CoseHeaders.serializer(),
                            CoseHeaders(algorithm = Cose.Algorithm.ES256),
                        ),
                    ),
                ),
            ),
        )
        val parsedRequest = wallet.parseAnnexCDeviceRequest(mismatchedChains.encodeToBase64Url())

        val rejection = assertFailsWith<IllegalArgumentException> {
            wallet.previewAnnexCPresentation(
                MobileWalletAnnexCRequest(
                    parsedRequest = parsedRequest,
                    verifiedOrigin = "https://verifier.example",
                    deviceRequestBase64Url = mismatchedChains.encodeToBase64Url(),
                    encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
                )
            )
        }
        assertTrue(
            rejection.causeChainMessages().any { it.contains("different certificate chains") },
            "Expected rejection because the signatures use different certificate chains, got: " +
                rejection.causeChainMessages(),
        )
    }

    @Test
    fun capabilityModelsFailClosedByDefault() = runTest {
        assertFalse(UnavailableMobileWalletCredentialRegistry.capabilities.platformAvailable)
        assertFalse(UnavailableMobileWalletCredentialRegistry.capabilities.registrationAvailable)
        // Untrusted, not Trusted: a wallet with no configured policy has no basis for identifying a
        // reader, however valid its signature.
        assertIs<MobileWalletReaderTrust.Untrusted>(
            UnconfiguredMobileWalletReaderTrustEvaluator.evaluate(emptyList())
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mobileWalletEventStreamDoesNotBackpressureSlowCollectors() = runTest {
        val stream = MobileWalletEventStream(replay = 1, extraBufferCapacity = 1)
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            stream.events.collect {
                delay(Long.MAX_VALUE.milliseconds)
            }
        }

        runCurrent()

        repeat(100) { index ->
            val emitted = stream.tryEmit(MobileWalletEvent.issuance_offer_resolved)

            assertTrue(emitted, "Progress event $index should not suspend or fail when the buffer is full")
        }

        collector.cancel()
    }

    private fun presentationRequestInfo(
        clientId: String = "https://verifier.example",
        nonce: String = "nonce-1",
    ) = MobileWalletPresentationRequestInfo(
        clientId = clientId,
        verifierMetadata = MobileWalletVerifierMetadata(
            display = MobileWalletMetadataDisplay(
                name = "Example Verifier",
                locale = "en",
                logoUri = null,
                logoAltText = null,
            ),
            clientUri = null,
            policyUri = null,
            termsOfServiceUri = null,
        ),
        responseUri = "https://verifier.example/direct-post",
        state = null,
        nonce = nonce,
        responseEncryption = MobileWalletResponseEncryption.NotRequired,
    )

    private fun presentationTransactionData(
        credentialQueryIds: List<String>,
    ) = MobileWalletTransactionDataItem(
        type = "example",
        displayName = "Example",
        credentialQueryIds = credentialQueryIds,
        supportedFields = emptyList(),
        rawJson = "{}",
        detailsJson = "{}",
    )

    private fun presentationDisclosure(
        selectivelyDisclosable: Boolean,
        required: Boolean,
        selectable: Boolean,
    ) = MobileWalletPresentationDisclosure(
        path = "$.claim",
        name = "claim",
        valueJson = "true",
        displayValue = "true",
        selectivelyDisclosable = selectivelyDisclosable,
        required = required,
        selectable = selectable,
    )

    private fun walletWithTrust(trustConfiguration: ClientIdTrustConfiguration): MobileWallet = MobileWallet(
        walletId = "trust-test-wallet",
        keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "unused-key", keyType = "Ed25519")),
        didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:unused", document = JsonObject(emptyMap()))),
        credentialStore = RecordingCredentialStore(),
        generateAndPersistKey = unusedKeyGenerator(),
        clientIdTrustConfiguration = trustConfiguration,
    )

    private suspend fun preRegisteredRequestUrl(verifierKey: Key): String {
        val requestObject = verifierKey.signJws(
            buildJsonObject {
                put("client_id", "verifier2")
                put("nonce", "nonce-123")
                put("response_type", "vp_token")
                put("response_mode", "direct_post")
                put("response_uri", "https://verifier.example/direct-post")
                put("dcql_query", buildJsonObject { put("credentials", buildJsonArray {}) })
            }.toString().encodeToByteArray(),
            mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
        )
        return URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
        }.buildString()
    }

    private fun unusedKeyGenerator(): suspend (MobileWalletKeyType) -> ManagedKeyMaterial =
        { error("This test must not bootstrap a new key") }

    /** A wallet holding one mDL, for Annex C tests that must reach reader-authentication checks. */
    private fun annexCWalletWithMdl(walletId: String): MobileWallet = MobileWallet(
        walletId = walletId,
        keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "unused-key", keyType = "secp256r1")),
        didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:unused", document = JsonObject(emptyMap()))),
        credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "mdl-1",
                credential = MdocsCredential(
                    credentialData = buildJsonObject {
                        put("org.iso.18013.5.1", buildJsonObject { put("given_name", "Ada") })
                    },
                    signed = MdocsExamples.mdocsExampleBase64Url,
                    docType = "org.iso.18013.5.1.mDL",
                ),
                label = "mDL",
            )
        ),
        generateAndPersistKey = unusedKeyGenerator(),
    )

    /**
     * The messages of a throwable and every cause beneath it.
     *
     * Annex C wraps reader-authentication failures in a positional message, so an assertion that only
     * read the outermost message would pass for any rejection and prove nothing about which check
     * fired.
     */
    private fun Throwable.causeChainMessages(): List<String> =
        generateSequence(this) { it.cause }.mapNotNull { it.message }.toList()

    @OptIn(ExperimentalEncodingApi::class)
    private fun String.decodeBase64Url(): ByteArray =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(this)

    private val displayJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private companion object {
        const val READER_CERTIFICATE_BASE64 =
            "MIIBsTCCAVegAwIBAgIUJklaRrIjkEZlDdPk2+qPneHHD6kwCgYIKoZIzj0EAwIwLjEQMA4GA1UEAwwHRXhhbXBsZTENMAsGA1UECgwEVGVzdDELMAkGA1UEBhMCVVMwHhcNMjYwMzMxMDkwNDMwWhcNMjcwMzMxMDkwNDMwWjAuMRAwDgYDVQQDDAdFeGFtcGxlMQ0wCwYDVQQKDARUZXN0MQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABM4ukI9BoHfMYjKmokWc5GMiN7DJBQAPPZBHXHhwmuQE+JyeRcamM+uCS1N+naE0itVbs7fQ/5xbujSSK9pYdb6jUzBRMB0GA1UdDgQWBBSwjqgulcWH4AqTJwPjBGj3VGIAsTAfBgNVHSMEGDAWgBSwjqgulcWH4AqTJwPjBGj3VGIAsTAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0gAMEUCIQCoWAleGRqR+kb+5SeRt/scogZPiQiM7wJ69tadEPPJwQIgdygIZSMQSXlxXbZ10QKtN6qSjggqFVUV4/Z2/pnBUBk="
        const val READER_ENCRYPTION_INFO =
            "gmVkY2FwaaJlbm9uY2VQAQIDBAUGBwgJCgsMDQ4PEHJyZWNpcGllbnRQdWJsaWNLZXmkAQIgASFYIM4ukI9BoHfMYjKmokWc5GMiN7DJBQAPPZBHXHhwmuQEIlgg-JyeRcamM-uCS1N-naE0itVbs7fQ_5xbujSSK9pYdb4"
        /** A second self-signed P-256 reader certificate, unrelated to [READER_CERTIFICATE_BASE64]. */
        const val OTHER_READER_CERTIFICATE_BASE64 =
            "MIIBzDCCAXGgAwIBAgIUePOjQNDuOrysvlG1mxyNml2jcdowCgYIKoZIzj0EAwIwMzEVMBMGA1UEAwwMT3RoZXIgUmVhZGVyMQ0wCwYDVQQKDARUZXN0MQswCQYDVQQGEwJVUzAeFw0yNjA4MDgwMzUxMDBaFw0zNjA4MDUwMzUxMDBaMDMxFTATBgNVBAMMDE90aGVyIFJlYWRlcjENMAsGA1UECgwEVGVzdDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQNs2RBhi04sO0GTNchxPgYGqPqwgZb8KpuUM+opp5fgziyF2KmDOQs1YtIG2a+9St+5C38fLcaWDUGuwrSLxeGo2MwYTAdBgNVHQ4EFgQUvjfoXfSFM14SPJMzryov6FxnbrkwHwYDVR0jBBgwFoAUvjfoXfSFM14SPJMzryov6FxnbrkwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCB4AwCgYIKoZIzj0EAwIDSQAwRgIhAJya7LMs0pjNW50GibaTk1i3QV4NXWfa3F6tv6JFgfG+AiEAwzZTMYiLt+Kh4yumndUoecTAp/fFdzvMRTHeZHamm3M="
        const val SIGNED_READER_REQUEST =
            "omd2ZXJzaW9uYzEuMGtkb2NSZXF1ZXN0c4GibGl0ZW1zUmVxdWVzdNgYWEqiZ2RvY1R5cGV1b3JnLmlzby4xODAxMy41LjEubURMam5hbWVTcGFjZXOhcW9yZy5pc28uMTgwMTMuNS4xoWpnaXZlbl9uYW1l9GpyZWFkZXJBdXRohEOhASahGCFZAbUwggGxMIIBV6ADAgECAhQmSVpGsiOQRmUN0-Tb6o-d4ccPqTAKBggqhkjOPQQDAjAuMRAwDgYDVQQDDAdFeGFtcGxlMQ0wCwYDVQQKDARUZXN0MQswCQYDVQQGEwJVUzAeFw0yNjAzMzEwOTA0MzBaFw0yNzAzMzEwOTA0MzBaMC4xEDAOBgNVBAMMB0V4YW1wbGUxDTALBgNVBAoMBFRlc3QxCzAJBgNVBAYTAlVTMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzi6Qj0Ggd8xiMqaiRZzkYyI3sMkFAA89kEdceHCa5AT4nJ5FxqYz64JLU36doTSK1Vuzt9D_nFu6NJIr2lh1vqNTMFEwHQYDVR0OBBYEFLCOqC6VxYfgCpMnA-MEaPdUYgCxMB8GA1UdIwQYMBaAFLCOqC6VxYfgCpMnA-MEaPdUYgCxMA8GA1UdEwEB_wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIhAKhYCV4ZGpH6Rv7lJ5G3-xyiBk-JCIzvAnr21p0Q88nBAiB3KAhlIxBJeXFdtnXRAq03qpKOCCoVVRXj9nb-mcFQGfZYQE524YTazDQiCCYBcZRzZHc0GfcMBDJVNIRZ1Svd3hXLG7pj8eTefRllnxRtj4nGQO-MQIJoRqPaDiMuIh1BLVU"
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

    /** Registry standing in for a platform whose registration call fails after the store committed. */
    private class FailingMetadataRegistry(private val failure: Throwable? = null) : MobileWalletCredentialRegistry {
        var replaceCalls = 0
        override val capabilities = UnavailableMobileWalletCredentialRegistry.capabilities

        override suspend fun replace(
            registryId: String,
            records: List<MobileWalletCredentialRegistryRecord>,
        ): MobileWalletCredentialRegistrationResult {
            replaceCalls++
            failure?.let { throw it }
            return MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = "Registry is unavailable",
            )
        }
    }

    private class PreloadedKeyStore(
        private val keyInfo: WalletKeyInfo,
        private val key: Key? = null,
        private val managedKey: ManagedKeyMaterial? = null,
        private val failIfLegacyKeyRequested: Boolean = false,
    ) : WalletKeyStore {
        var listKeysCalls = 0
        var managedKeyLookupCalls = 0
        val removedKeyIds = mutableListOf<String>()

        override suspend fun getKey(keyId: String): Key? {
            check(!failIfLegacyKeyRequested) { "Managed-key bootstrap must not load a legacy key" }
            return key.takeIf { keyId == keyInfo.keyId }
        }

        override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): ManagedKeyMaterial? {
            managedKeyLookupCalls++
            return managedKey.takeIf { keyId == keyInfo.keyId }
        }

        override suspend fun listKeys(): Flow<WalletKeyInfo> {
            listKeysCalls++
            return listOf(keyInfo).asFlow()
        }

        override suspend fun addKey(key: Key): String =
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

        /** How often the wallet enumerated stored credentials, so tests can assert it never did. */
        var streamCount = 0
            private set

        override suspend fun getCredential(id: String): StoredCredential? = credentials.firstOrNull { it.id == id }

        override suspend fun listCredentials(): Flow<StoredCredential> {
            streamCount++
            return credentials.toList().asFlow()
        }

        override suspend fun addCredential(entry: StoredCredential) =
            error("Recording credential store should not add credentials in this test")

        override suspend fun removeCredential(id: String): Boolean {
            removedCredentialIds += id
            return true
        }
    }

    private class RecordingIssuanceSessionStore(
        vararg records: WalletIssuanceSessionRecord,
    ) : WalletIssuanceSessionStore {
        val records = records.associateByTo(linkedMapOf()) { it.id }

        override suspend fun get(id: String): WalletIssuanceSessionRecord? = records[id]
        override suspend fun list(): List<WalletIssuanceSessionRecord> = records.values.toList()
        override suspend fun put(record: WalletIssuanceSessionRecord) {
            records[record.id] = record
        }
        override suspend fun remove(id: String): Boolean = records.remove(id) != null
    }
}
