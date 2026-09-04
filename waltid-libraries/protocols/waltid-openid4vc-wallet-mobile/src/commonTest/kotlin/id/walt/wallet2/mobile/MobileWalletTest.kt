@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseKey
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.cose.toCoseSigner
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
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key as ManagedKeyMaterial
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceRequestInfo
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import id.walt.mdoc.objects.deviceretrieval.UseCase
import id.walt.mdoc.objects.document.Document
import id.walt.openid4vci.offers.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.prefixes.Unsupported
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import id.walt.x509.X509KeyUsage
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletPublicKeyMaterial
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.data.withImportedHolderKeyBinding
import id.walt.wallet2.handlers.WalletIssuanceGrant
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import id.walt.wallet2.handlers.WalletIssuanceSessionStore
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.MobileWalletKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.request.RequestObjectAuthentication
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
        val (
            walletId,
            defaultKeyType,
            attestationConfig,
            persistence,
            onEvent,
            preferredLocales,
            transactionDataProfiles,
            credentialIssuerMetadataTrustResolver,
        ) = config

        assertEquals("default", walletId)
        assertEquals(MobileWalletKeyType.secp256r1, defaultKeyType)
        assertEquals(null, attestationConfig)
        assertEquals(MobileWalletPersistence(), persistence)
        assertEquals(emptyList(), preferredLocales)
        assertEquals(emptyList(), transactionDataProfiles)
        assertEquals(null, credentialIssuerMetadataTrustResolver)
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
                        jwks = ClientMetadata.Jwks(
                            listOf(
                                JsonObject(
                                    verifierKey.getPublicKey().exportJWKObject() +
                                        ("kid" to JsonPrimitive(verifierKey.getKeyId())),
                                ),
                            ),
                        ),
                    )
                ),
            )
        )

        val preview = assertIs<MobileWalletPresentationPreviewResult.Invalid>(
            wallet.previewPresentation(requestUrl)
        )

        assertEquals("verifier2", preview.request.clientId)
        assertEquals(MobileWalletPresentationErrorCode.invalidRequest, preview.errorCode)
        val authentication = assertIs<MobileWalletRequestAuthentication.Authenticated>(
            preview.request.requestAuthentication,
        )
        assertEquals(Url(requestUrl).parameters["request"], authentication.compactRequestObject)
        assertEquals("EdDSA", authentication.algorithm)
        assertEquals(verifierKey.getKeyId(), authentication.keyId)
        assertEquals(MobileWalletClientIdScheme.PRE_REGISTERED, authentication.clientIdScheme)
    }

    @Test
    fun unsupportedAuthenticatedClientIdFailsClosed() {
        val resolved = ResolvedAuthorizationRequest.AuthenticatedRequestObject(
            authorizationRequest = AuthorizationRequest(clientId = "unsupported:verifier"),
            requestObject = "header.payload.signature",
            authentication = RequestObjectAuthentication(
                clientId = Unsupported(prefix = "unsupported", rawValue = "unsupported:verifier"),
                algorithm = "ES256",
                keyId = null,
            ),
        )

        val failure = assertFailsWith<IllegalStateException> {
            resolved.toMobileRequestAuthentication()
        }

        assertEquals("Unsupported client identifier cannot be authenticated: unsupported", failure.message)
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
        val runtime = CryptoRuntime(softwareProviders = defaultSoftwareKeyProviders())
        val managedKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("managed-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
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
        assertTrue(bootstrap.publicJwk.contains("\"kty\""), bootstrap.publicJwk)
        assertEquals(2, keyStore.managedKeyLookupCalls)
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
                requestAuthentication = MobileWalletRequestAuthentication.Unauthenticated,
                responseUri = null,
                state = null,
                nonce = null,
                responseEncryption = MobileWalletResponseEncryption.NotRequired,
            )
        }

        val context = MobileWalletPresentationRequestContext(
            clientId = "https://verifier.example",
            verifierMetadata = null,
            requestAuthentication = MobileWalletRequestAuthentication.Unauthenticated,
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
                requestAuthentication = MobileWalletRequestAuthentication.Unauthenticated,
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
            generateAndPersistKey = { _, _ -> error("Registry refresh must not generate keys") },
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
        assertNull(record.iconPng)
        assertTrue(record.cardArtImageUris.isEmpty())
        assertNull(record.cardArtBackgroundColor)
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
            generateAndPersistKey = { _, _ -> error("Registry refresh must not generate keys") },
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
            generateAndPersistKey = { _, _ -> error("Registry refresh must not generate keys") },
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
            generateAndPersistKey = { _, _ -> error("Registry refresh must not generate keys") },
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
            generateAndPersistKey = { _, _ -> error("Registry refresh must not generate keys") },
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
            generateAndPersistKey = { _, _ -> error("Registry failure must not generate keys") },
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

    /**
     * The issuance counterpart of the deletion case above: a registry that rejects the projection must
     * not turn a stored credential into [WalletIssuanceOutcome.Failed], leaving the wallet holding a
     * credential the application was told it never received.
     */
    @Test
    fun failedRegistrySynchronizationDoesNotTurnStoredIssuanceIntoFailedIssuance() = runTest {
        val registry = FailingMetadataRegistry(IllegalStateException("Credential Manager rejected the registry"))
        val holderKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuance-holder-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val credentialStore = InMemoryCredentialStore()
        val wallet = MobileWallet(
            walletId = "issuance-registry-failure-wallet",
            keyStore = InMemoryMobileWalletKeyStore().also { it.addCrypto2Key(holderKey) },
            didStore = InMemoryDidStore().also {
                it.addDid(WalletDidEntry(did = "did:key:holder", document = JsonObject(emptyMap())))
            },
            credentialStore = credentialStore,
            generateAndPersistKey = { _, _ -> error("Issuance must not generate keys") },
            credentialRegistry = registry,
            issuanceHttpClient = mockIssuer(),
        )

        val session = wallet.startIssuance(
            MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.Uri(preAuthorizedOfferUrl()))
        )
        val outcome = assertIs<WalletIssuanceOutcome.Stored>(wallet.continuePreAuthorizedIssuance(session.id))

        // The credential is in the wallet and reported as issued, both of which the registry cannot revoke.
        val storedId = outcome.credentialIds.single()
        assertNotNull(credentialStore.getCredential(storedId))
        assertEquals(listOf(storedId), wallet.credentials().map { it.id })

        // The projection failure is observable, but only where a stale projection is reported.
        assertEquals(1, registry.replaceCalls)
        val reported = assertNotNull(wallet.digitalCredentialRegistration.value)
        assertFalse(reported.available)
        assertEquals("Credential Manager rejected the registry", reported.reason)
    }

    @Test
    fun startIssuanceAcceptsInlineOfferJsonUsedByDigitalCredentialsCreate() = runTest {
        val holderKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("offer-json-holder-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val wallet = MobileWallet(
            walletId = "offer-json-wallet",
            keyStore = InMemoryMobileWalletKeyStore().also { it.addCrypto2Key(holderKey) },
            didStore = InMemoryDidStore().also {
                it.addDid(WalletDidEntry(did = "did:key:holder", document = JsonObject(emptyMap())))
            },
            credentialStore = InMemoryCredentialStore(),
            generateAndPersistKey = { _, _ -> error("Issuance must not generate keys") },
            issuanceHttpClient = mockIssuer(),
        )

        val session = wallet.startIssuance(
            MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.InlineJson(preAuthorizedOfferJson()))
        )
        assertEquals(WalletIssuanceGrant.PRE_AUTHORIZED_CODE, session.offer.grant)

        val outcome = assertIs<WalletIssuanceOutcome.Stored>(wallet.continuePreAuthorizedIssuance(session.id))
        assertEquals(1, outcome.credentialIds.size)
    }

    @Test
    fun issuanceRequestRejectsBlankOffer() {
        assertFailsWith<IllegalArgumentException> {
            MobileWalletCredentialOffer.Uri("")
        }
        assertFailsWith<IllegalArgumentException> {
            MobileWalletCredentialOffer.InlineJson("   ")
        }
    }

    /**
     * A credential-set change has to *request host reconciliation*, not merely re-publish a projection.
     *
     * On iOS this registry writes a desired state into a shared container, while Apple's
     * `IdentityDocumentProviderRegistrationStore` is writable only by the host app, so a changed
     * projection alone does not make the platform offer the credential.
     */
    @Test
    fun aCredentialSetChangeAsksTheHostToReconcileThePlatformRegistrations() = runTest {
        val registry = RecordingMetadataRegistry()
        val reconciliationRequests = mutableListOf<Int>()
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "pid-1",
                credential = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2).second,
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "registry-notification-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            generateAndPersistKey = { _, _ -> error("Registry notification must not generate keys") },
            credentialRegistry = registry,
            // How many projections had been published when the host was notified, which proves the
            // notification follows the publish rather than racing it.
            onDigitalCredentialRegistryChanged = { reconciliationRequests += registry.replacements.size },
        )

        assertTrue(wallet.deleteCredential("pid-1"))
        assertEquals(listOf(1), reconciliationRequests, "deletion has to ask the host to reconcile, once")

        // The direct retry entry point is not a credential-set change: it is the host's own way of
        // re-publishing, so notifying there would call the host back into itself.
        wallet.refreshDigitalCredentialRegistration()
        assertEquals(listOf(1), reconciliationRequests)
    }

    /**
     * The host is told even when publishing the desired state failed, because an earlier projection may
     * still be pending; and since the credential change is already committed, a host that throws must
     * not turn a completed deletion into a failed one.
     */
    @Test
    fun theHostIsAskedToReconcileEvenWhenPublishingFailedAndItsOwnFailureIsContained() = runTest {
        val registry = FailingMetadataRegistry(IllegalStateException("Registry rejected the projection"))
        var reconciliationRequests = 0
        val credentialStore = RecordingCredentialStore(
            StoredCredential(
                id = "pid-1",
                credential = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2).second,
                label = "PID",
            )
        )
        val wallet = MobileWallet(
            walletId = "registry-notification-failure-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = credentialStore,
            generateAndPersistKey = { _, _ -> error("Registry notification must not generate keys") },
            credentialRegistry = registry,
            onDigitalCredentialRegistryChanged = {
                reconciliationRequests++
                throw IllegalStateException("IdentityDocumentServices is unavailable")
            },
        )

        assertTrue(wallet.deleteCredential("pid-1"))
        assertEquals(listOf("pid-1"), credentialStore.removedCredentialIds)
        assertEquals(1, reconciliationRequests)
        assertFalse(assertNotNull(wallet.digitalCredentialRegistration.value).available)
    }

    @Test
    fun unavailableRegistrySurfacesItsReasonWithoutThrowing() = runTest {
        val registry = FailingMetadataRegistry()
        val wallet = MobileWallet(
            walletId = "registry-unavailable-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = { _, _ -> error("Registry refresh must not generate keys") },
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
            generateAndPersistKey = { _, _ -> error("Parsing must not generate keys") },
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

    @Test
    fun annexCRequestReadsTheDcApiEnvelopeAndRefusesAnIncompleteOrForeignOne() = runTest {
        val wallet = MobileWallet(
            walletId = "annex-c-envelope-wallet",
            keyStore = PreloadedKeyStore(WalletKeyInfo(keyId = "custom-key", keyType = "secp256r1")),
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(),
            generateAndPersistKey = { _, _ -> error("Reading a request must not generate keys") },
        )
        val deviceRequest = DeviceRequest(
            docType = "org.iso.18013.5.1.mDL",
            requestedElements = mapOf("org.iso.18013.5.1" to listOf("given_name")),
        ).encodeToBase64Url()
        fun envelope(protocol: String, dataJson: String) = MobileWalletDigitalCredentialRequest(
            protocol = protocol,
            dataJson = dataJson,
            verifiedOrigin = "https://verifier.example",
            selectedRegistryEntryIds = listOf("entry-1"),
        )

        val request = wallet.annexCRequest(
            envelope(
                MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                """{"deviceRequest":"$deviceRequest","encryptionInfo":"encryption-info"}""",
            )
        )

        assertEquals(deviceRequest, request.deviceRequestBase64Url)
        assertEquals("encryption-info", request.encryptionInfoBase64Url)
        assertEquals("https://verifier.example", request.verifiedOrigin)
        assertEquals(listOf("entry-1"), request.selectedRegistryEntryIds)
        assertEquals("org.iso.18013.5.1.mDL", request.parsedRequest.documents.single().docType)
        // Both raw fields are mandatory: a half-populated Annex C request would otherwise reach the
        // engine as Apple's pre-consent shape and be previewed without reader authentication.
        assertFailsWith<IllegalArgumentException> {
            wallet.annexCRequest(
                envelope(
                    MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                    """{"deviceRequest":"$deviceRequest"}""",
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            wallet.annexCRequest(
                envelope(
                    MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                    """{"deviceRequest":"$deviceRequest","encryptionInfo":"encryption-info"}""",
                )
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun annexCReaderAuthenticationUsesVerifierTranscriptBuildsResponseAndRejectsTampering() = runTest {
        val origin = "https://verifier.example"
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
        val holderKeyStore = PreloadedKeyStore(
            WalletKeyInfo(keyId = holderKeyId, keyType = "Ed25519"),
            managedKey = holderSigner,
        )
        val wallet = MobileWallet(
            walletId = "annex-c-reader-auth-wallet",
            keyStore = holderKeyStore,
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(
                annexCBoundMdl(holderSigner, holderKeyStore),
            ),
            generateAndPersistKey = { _, _ -> error("Reader-authentication preview must not generate keys") },
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
     * The four non-trusted reader states must stay distinguishable, and none of them may be produced by
     * a signature that failed to verify. On Apple's deferred path the preview cannot check the signature
     * at all, so a bad one arriving with the raw request has to reject the submission.
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
        val holderKeyStore = PreloadedKeyStore(
            WalletKeyInfo(keyId = holderSigner.id.value, keyType = "Ed25519"),
            managedKey = holderSigner,
        )
        // No readerTrustEvaluator: this exercises the default policy, which must never report Trusted.
        val wallet = MobileWallet(
            walletId = "annex-c-reader-trust-states-wallet",
            keyStore = holderKeyStore,
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(
                annexCBoundMdl(holderSigner, holderKeyStore),
            ),
            generateAndPersistKey = { _, _ -> error("Reader-trust previews must not generate keys") },
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
        // The failure must be the signature, not an earlier structural check.
        assertTrue(
            rejection.message?.contains("signature") == true,
            "Expected a reader-authentication signature rejection, got: ${rejection.message}",
        )
    }

    /**
     * A request whose raw bytes were available at consent must be answered byte for byte.
     *
     * Every rejected case below is a *valid* request that parses to exactly what the user saw, because
     * the parsed request carries no reader authentication, no `deviceRequestInfo` and no version.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun annexCAnswersOnlyTheExactRawRequestConsentWasGivenFor() = runTest {
        val origin = "https://verifier.example"
        val wallet = annexCWalletWithMdlAndHolderKey("annex-c-consent-binding-wallet")
        val signedRequest = DeviceRequest.decodeFromBase64Url(SIGNED_READER_REQUEST)
        val docRequest = signedRequest.docRequests.single()
        val transcript = AnnexCTranscriptBuilder.buildSessionTranscript(READER_ENCRYPTION_INFO, origin)

        val preview = wallet.previewAnnexCPresentation(
            MobileWalletAnnexCRequest(
                parsedRequest = wallet.parseAnnexCDeviceRequest(SIGNED_READER_REQUEST),
                verifiedOrigin = origin,
                deviceRequestBase64Url = SIGNED_READER_REQUEST,
                encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
            )
        )
        assertIs<MobileWalletReaderTrust.Untrusted>(preview.readerTrust)

        suspend fun submit(deviceRequestBase64Url: String) = wallet.submitAnnexCPresentation(
            MobileWalletAnnexCSubmission(
                requestId = preview.requestId,
                verifiedOrigin = origin,
                deviceRequestBase64Url = deviceRequestBase64Url,
                encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
                selectedCredentialOptions = preview.credentialOptions.map {
                    MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId)
                },
            )
        )

        suspend fun assertRejected(deviceRequestBase64Url: String, case: String) {
            val rejection = assertFailsWith<IllegalArgumentException> { submit(deviceRequestBase64Url) }
            assertTrue(
                rejection.message?.contains("deviceRequest changed after consent") == true,
                "Expected $case to be rejected as a changed request, got: ${rejection.message}",
            )
        }

        // Dropping reader authentication leaves a request that is answerable on its own, with the
        // permitted trust state NotAuthenticated - but not the one consented to.
        assertRejected(
            signedRequest.copy(docRequests = listOf(docRequest.copy(readerAuth = null)))
                .encodeToBase64Url(),
            "a request with reader authentication removed",
        )

        // A second reader whose signature genuinely verifies - proven by the preview below, which an
        // invalid substitute would fail, so the rejection is not a verification failure in disguise.
        val otherReaderKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("other-reader-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val otherReaderCertificate = GenericX509CertificateBuilder().buildDer(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(commonName = "Substitute Reader"),
                keyUsage = setOf(X509KeyUsage.DigitalSignature),
            ),
            subjectPublicKey = otherReaderKey,
            signingKey = otherReaderKey,
            signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        )
        val substitutedReader = signedRequest.copy(
            docRequests = listOf(
                docRequest.copy(
                    readerAuth = CoseSign1.createAndSignDetached(
                        protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
                        unprotectedHeaders = CoseHeaders(
                            x5chain = listOf(CoseCertificate(otherReaderCertificate.bytes.toByteArray())),
                        ),
                        detachedPayload = ReaderAuthenticationPayloads.forDocument(transcript, docRequest.itemsRequest),
                        signer = otherReaderKey.toCoseSigner(Cose.Algorithm.ES256),
                    ),
                )
            ),
        ).encodeToBase64Url()
        assertIs<MobileWalletReaderTrust.Untrusted>(
            wallet.previewAnnexCPresentation(
                MobileWalletAnnexCRequest(
                    parsedRequest = wallet.parseAnnexCDeviceRequest(substitutedReader),
                    verifiedOrigin = origin,
                    deviceRequestBase64Url = substitutedReader,
                    encryptionInfoBase64Url = READER_ENCRYPTION_INFO,
                )
            ).readerTrust,
        )
        assertRejected(substitutedReader, "a request re-signed by a different valid reader")

        // deviceRequestInfo is outside the per-document reader-authentication payload, so this
        // request keeps the original reader's valid signature while changing what was requested.
        assertRejected(
            signedRequest.copy(
                deviceRequestInfo = ByteStringWrapper(
                    DeviceRequestInfo(
                        useCases = listOf(UseCase(mandatory = true, documentSets = listOf(listOf(0u)))),
                    )
                ),
            ).encodeToBase64Url(),
            "a request carrying added deviceRequestInfo",
        )

        // Comparison is on decoded bytes, not on spelling: the fixture is unpadded, and its padded form
        // is the same request.
        val response = submit("$SIGNED_READER_REQUEST=")
        assertEquals(MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C, response.protocol)
        assertTrue(
            displayJson.parseToJsonElement(response.dataJson).jsonObject["response"]
                ?.jsonPrimitive?.content?.isNotBlank() == true
        )
    }

    /**
     * A recipient key the response could never be HPKE-sealed to must stop the request before the
     * consent dialog, not at submission: discovering it while sealing means the user has already been
     * asked to disclose claims for a request that cannot be answered. Both the rejection and the absence
     * of any credential read are asserted, the latter standing in for "no consent screen was prepared".
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
     * Reader authentication must be restricted to the algorithms ISO 18013-5 §9.1.3.4 permits, read from
     * the *protected* header where they are signed over.
     *
     * `ESP256` is the sharp case: a legitimate, fully-specified P-256 ECDSA identifier the allowlist
     * excludes, so a wallet trusting `alg` blindly would verify it. The assertion walks the cause chain
     * because both call sites wrap the failure, and the outer message alone would also match an ordinary
     * bad signature.
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
     * Otherwise whichever chain reached [MobileWalletReaderTrustEvaluator] would decide the trust state
     * shown to the user, and the reader identity displayed at consent need not be the one that
     * authenticated the request. The mismatch is checked before signature verification, so the second
     * signature here can be arbitrary bytes.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun annexCRejectsReaderAuthenticationSignaturesFromDifferentCertificateChains() = runTest {
        val wallet = annexCWalletWithMdl("annex-c-reader-chain-mismatch-wallet")
        val signedRequest = DeviceRequest.decodeFromBase64Url(SIGNED_READER_REQUEST)
        val docRequest = signedRequest.docRequests.single()
        val readerAuth = requireNotNull(docRequest.readerAuth)
        // The first signature establishes the chain the rest are compared against, so two doc requests
        // are needed to exercise this at all.
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
        requestAuthentication = MobileWalletRequestAuthentication.Unauthenticated,
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
                put("aud", AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE)
                put("nonce", "nonce-123")
                put("response_type", "vp_token")
                put("response_mode", "direct_post")
                put("response_uri", "https://verifier.example/direct-post")
                put("dcql_query", buildJsonObject { put("credentials", buildJsonArray {}) })
            }.toString().encodeToByteArray(),
            mapOf(
                "typ" to JsonPrimitive("oauth-authz-req+jwt"),
                "kid" to JsonPrimitive(verifierKey.getKeyId()),
            ),
        )
        return URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
        }.buildString()
    }

    private fun unusedKeyGenerator(): suspend (MobileWalletKeyType, KeyUseAuthorizationPolicy) -> ManagedKeyMaterial =
        { _, _ -> error("This test must not bootstrap a new key") }

    /** A pre-authorized offer carried inline, so resolving it needs no offer fetch of its own. */
    private fun preAuthorizedOfferUrl(): String = URLBuilder(CROSS_DEVICE_CREDENTIAL_OFFER_URL).apply {
        parameters.append("credential_offer", preAuthorizedOfferJson())
    }.buildString()

    /** Credential Offer JSON object as delivered by Digital Credentials API create requests. */
    private fun preAuthorizedOfferJson(): String = buildJsonObject {
        put("credential_issuer", MOCK_ISSUER)
        put("credential_configuration_ids", buildJsonArray { add(JsonPrimitive(MOCK_CONFIGURATION_ID)) })
        put("grants", buildJsonObject {
            put(
                "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                buildJsonObject { put("pre-authorized_code", "pre-code") },
            )
        })
    }.toString()

    /**
     * The smallest OpenID4VCI issuer that answers one pre-authorized request with one credential.
     *
     * Only the endpoints the flow reaches are served, so a request the wallet should not make surfaces
     * as a 404 instead of being silently absorbed.
     */
    private fun mockIssuer(): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.toString()) {
                    "$MOCK_ISSUER/.well-known/openid-credential-issuer" -> jsonResponse(
                        """
                        {
                          "credential_issuer":"$MOCK_ISSUER",
                          "credential_endpoint":"$MOCK_ISSUER/credential",
                          "credential_configurations_supported":{
                            "$MOCK_CONFIGURATION_ID":{
                              "format":"dc+sd-jwt",
                              "vct":"urn:eu.europa.ec.eudi:pid:1"
                            }
                          }
                        }
                        """.trimIndent()
                    )

                    "$MOCK_ISSUER/.well-known/oauth-authorization-server" -> jsonResponse(
                        """
                        {
                          "issuer":"$MOCK_ISSUER",
                          "token_endpoint":"$MOCK_ISSUER/token",
                          "response_types_supported":["code"],
                          "grant_types_supported":["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
                        }
                        """.trimIndent()
                    )

                    "$MOCK_ISSUER/token" ->
                        jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")

                    "$MOCK_ISSUER/credential" -> jsonResponse(
                        buildJsonObject {
                            put("credentials", buildJsonArray {
                                add(buildJsonObject { put("credential", SdJwtExamples.sdJwtVcSignedExample2) })
                            })
                        }.toString()
                    )

                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        }
        install(ContentNegotiation) { json(displayJson) }
    }

    private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    /** A wallet holding one bound mDL, for Annex C tests that must reach reader-authentication checks. */
    private suspend fun annexCWalletWithMdl(walletId: String): MobileWallet =
        annexCWalletWithMdlAndHolderKey(walletId)

    /** [annexCWalletWithMdl] with a signing key, for Annex C tests that build a response. */
    private suspend fun annexCWalletWithMdlAndHolderKey(walletId: String): MobileWallet {
        // A managed crypto2 key, not an imported private JWK: Android's software provider only
        // accepts RSA private JWK material, so importing this Ed25519 key would fail there.
        val holderSigner = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder-key"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val holderKeyStore = PreloadedKeyStore(
            WalletKeyInfo(keyId = holderSigner.id.value, keyType = "Ed25519"),
            managedKey = holderSigner,
        )
        return MobileWallet(
            walletId = walletId,
            keyStore = holderKeyStore,
            didStore = PreloadedDidStore(WalletDidEntry(did = "did:key:custom", document = JsonObject(emptyMap()))),
            credentialStore = RecordingCredentialStore(
                annexCBoundMdl(holderSigner, holderKeyStore),
            ),
            generateAndPersistKey = unusedKeyGenerator(),
        )
    }

    /** Issues an mDL to [holderKey] and persists its exact provider-qualified holder-key binding. */
    private suspend fun annexCBoundMdl(
        holderKey: ManagedKeyMaterial,
        keyStore: MobileWalletKeyStore,
    ): StoredCredential {
        val issuerKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("annex-c-issuer-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val signatureAlgorithm = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )
        val certificate = X509CertificateUtil.createSelfSignedCertificate(issuerKey, signatureAlgorithm) {
            subjectDn = "CN=Annex C wallet test issuer"
        }
        val holderPublicJwk = holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = holderPublicJwk.toCoseKey(),
            docType = "org.iso.18013.5.1.mDL",
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(
                    "org.iso.18013.5.1" to JsonObject(mapOf("given_name" to JsonPrimitive("Ada")))
                )
            ),
        )
        val raw = coseCompliantCbor.encodeToByteArray(
            Document.serializer(),
            Document(docType = "org.iso.18013.5.1.mDL", issuerSigned = issuerSigned),
        ).encodeToBase64Url()
        val credential = StoredCredential(
            id = "mdl-1",
            credential = CredentialParser.detectAndParse(raw).second,
            label = "mDL",
        )
        return Wallet(id = "annex-c-binding", keyStores = listOf(keyStore))
            .withImportedHolderKeyBinding(credential)
    }

    /**
     * The messages of a throwable and every cause beneath it.
     *
     * Annex C wraps reader-authentication failures in a positional message, so matching the outermost
     * message alone would pass for any rejection and prove nothing about which check fired.
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
        const val MOCK_ISSUER = "https://issuer.example"
        const val MOCK_CONFIGURATION_ID = "test-credential"
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
        private val authorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
    ) : MobileWalletKeyStore {
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

        override suspend fun getPublicKeyMaterial(keyId: String): WalletPublicKeyMaterial? {
            val matchingKey = managedKey.takeIf { keyId == keyInfo.keyId } ?: return null
            val publicJwk = matchingKey.capabilities.publicKeyExporter
                ?.exportPublicKey() as? EncodedKey.Jwk
                ?: return null
            return WalletPublicKeyMaterial(publicJwk)
        }

        override suspend fun listKeys(): Flow<WalletKeyInfo> {
            listKeysCalls++
            return listOf(keyInfo).asFlow()
        }

        override suspend fun keyUseAuthorizationPolicy(keyId: String): KeyUseAuthorizationPolicy? =
            authorizationPolicy.takeIf { keyId == keyInfo.keyId }

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
