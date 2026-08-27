@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.data

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.protectedAlgorithm
import id.walt.cose.toCoseKey
import id.walt.cose.verifyDetached
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.handlers.MatchCredentialsFromStoreRequest
import id.walt.wallet2.handlers.ImportCredentialRequest
import id.walt.wallet2.handlers.WalletCredentialHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class HolderKeyBindingTest {

    @Test
    fun `binding serialization persists version and algorithm discriminators`() = runTest {
        val holderKey = signingKey("holder")
        val store = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val binding = Wallet(id = "wallet", keyStores = listOf(store))
            .withImportedHolderKeyBinding(mdocCredential(holderKey))
            .holderKeyBinding!!

        val encoded = Json.encodeToString(binding)
        val json = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            HolderKeyBinding.CURRENT_SCHEMA_VERSION,
            json["schemaVersion"]?.jsonPrimitive?.content?.toInt(),
        )
        assertEquals(
            HolderKeyBinding.CURRENT_EXTRACTOR_VERSION,
            json["extractorVersion"]?.jsonPrimitive?.content?.toInt(),
        )
        assertEquals(
            PublicKeyThumbprint.RFC7638_SHA256,
            json["publicKeyThumbprint"]?.jsonObject?.get("algorithm")?.jsonPrimitive?.content,
        )
        assertEquals(binding, Json.decodeFromString<HolderKeyBinding>(encoded))
    }

    @Test
    fun `issuance binding resolves the exact key after unrelated key rotation`() = runTest {
        val holderKey = signingKey("holder")
        val keyStore = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val wallet = Wallet(id = "wallet", keyStores = listOf(keyStore))
        val credential = mdocCredential(holderKey)

        val bound = wallet.withVerifiedIssuanceHolderKeyBinding(
            credential = credential,
            keyMaterial = wallet.resolveKeyMaterial(holderKey.id.value, setOf(KeyUsage.SIGN))!!,
        )

        val unrelated = signingKey("new-default")
        keyStore.addCrypto2Key(unrelated)
        val resolved = wallet.resolveHolderKey(bound, setOf(KeyUsage.SIGN))

        assertSame(holderKey, resolved.keyMaterial.crypto2Key)
        assertEquals(HolderKeyBindingOrigin.ISSUANCE, resolved.binding.origin)
        assertNotEquals(unrelated.id.value, resolved.keyMaterial.keyId)
    }

    @Test
    fun `holder binding resolves providers with the requested signing usage`() = runTest {
        val holderKey = signingKey("holder")
        val keyStore = UsageRequiringKeyStore(holderKey)
        val wallet = Wallet(id = "wallet", keyStores = listOf(keyStore))
        val credential = mdocCredential(holderKey)

        val bound = wallet.withVerifiedIssuanceHolderKeyBinding(
            credential = credential,
            keyMaterial = wallet.resolveKeyMaterial(holderKey.id.value, setOf(KeyUsage.SIGN))!!,
        )
        val resolved = wallet.resolveHolderKey(bound, setOf(KeyUsage.SIGN))

        assertSame(holderKey, resolved.keyMaterial.crypto2Key)
        assertEquals(List(3) { setOf(KeyUsage.SIGN) }, keyStore.requestedUsages)
    }

    @Test
    fun `unbound mdoc is not migrated implicitly`() = runTest {
        val holderKey = signingKey("holder")
        val keyStore = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val wallet = Wallet(id = "wallet", keyStores = listOf(keyStore))

        val failure = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(mdocCredential(holderKey), setOf(KeyUsage.SIGN))
        }

        assertEquals(HolderKeyBindingErrorCode.BINDING_MISSING, failure.code)
    }

    @Test
    fun `store matching does not offer an unbound mdoc for presentation`() = runTest {
        val holderKey = signingKey("holder")
        val unbound = mdocCredential(holderKey)
        val credentialStore = InMemoryCredentialStore().apply {
            addCredential(unbound)
        }
        val wallet = Wallet(
            id = "wallet",
            keyStores = listOf(InMemoryKeyStore().apply { addCrypto2Key(holderKey) }),
            credentialStores = listOf(credentialStore),
        )
        val request = MatchCredentialsFromStoreRequest(
            DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "mdl",
                        format = CredentialFormat.MSO_MDOC,
                        meta = MsoMdocMeta(doctypeValue = MDOC_DOCTYPE),
                    )
                )
            )
        )

        val failure = assertFailsWith<HolderKeyBindingException> {
            WalletPresentationHandler.matchCredentialsFromStore(
                wallet = wallet,
                request = request,
            )
        }

        assertEquals(HolderKeyBindingErrorCode.BINDING_MISSING, failure.code)

        credentialStore.addCredential(
            wallet.withRequiredImportedHolderKeyBinding(unbound)
        )
        val result = WalletPresentationHandler.matchCredentialsFromStore(wallet, request)

        assertEquals(listOf("mdl"), result.matchedQueryIds)
        assertEquals(1, result.matchCount)
        assertEquals(mapOf("mdl" to listOf(unbound.id)), result.matchedCredentialIds)
    }

    @Test
    fun `store matching accepts a bound alternative to an unbound mdoc`() = runTest {
        val fixture = mdocMatchingFixture()
        val request = MatchCredentialsFromStoreRequest(
            DcqlQuery(
                credentials = listOf(
                    mdocQuery("mdl", MDOC_DOCTYPE),
                    mdocQuery("pid", PID_DOCTYPE),
                ),
                credentialSets = listOf(
                    CredentialSetQuery(options = listOf(listOf("mdl"), listOf("pid"))),
                ),
            ),
        )

        val result = WalletPresentationHandler.matchCredentialsFromStore(fixture.wallet, request)

        assertEquals(1, result.matchCount)
        assertEquals(emptyList(), result.matchedCredentialIds["mdl"])
        assertEquals(listOf(fixture.boundCredential.id), result.matchedCredentialIds["pid"])
    }

    @Test
    fun `automatic selection chooses a bound mdoc when an unbound match is stored first`() = runTest {
        val unboundKey = signingKey("unbound-holder")
        val boundKey = signingKey("bound-holder")
        val credentialStore = InMemoryCredentialStore()
        val wallet = Wallet(
            id = "matching-wallet",
            keyStores = listOf(InMemoryKeyStore().apply {
                addCrypto2Key(unboundKey)
                addCrypto2Key(boundKey)
            }),
            credentialStores = listOf(credentialStore),
        )
        credentialStore.addCredential(mdocCredential(unboundKey, id = "unbound-mdl"))
        val boundCredential = wallet.withImportedHolderKeyBinding(
            mdocCredential(boundKey, id = "bound-mdl"),
        ).also { credentialStore.addCredential(it) }
        val query = DcqlQuery(credentials = listOf(mdocQuery("mdl", MDOC_DOCTYPE)))

        val selected = WalletPresentationHandler.selectPresentableFromStores(
            wallet = wallet,
            query = query,
        )

        assertEquals(
            listOf(boundCredential.id),
            selected.getValue("mdl").map { it.credential.id },
        )
    }

    @Test
    fun `store matching ignores an unavailable optional mdoc set`() = runTest {
        val fixture = mdocMatchingFixture()
        val request = MatchCredentialsFromStoreRequest(
            DcqlQuery(
                credentials = listOf(
                    mdocQuery("optional", MDOC_DOCTYPE),
                    mdocQuery("required", PID_DOCTYPE),
                ),
                credentialSets = listOf(
                    CredentialSetQuery(options = listOf(listOf("required"))),
                    CredentialSetQuery(required = false, options = listOf(listOf("optional"))),
                ),
            ),
        )

        val result = WalletPresentationHandler.matchCredentialsFromStore(fixture.wallet, request)

        assertEquals(1, result.matchCount)
        assertEquals(emptyList(), result.matchedCredentialIds["optional"])
        assertEquals(listOf(fixture.boundCredential.id), result.matchedCredentialIds["required"])
    }

    @Test
    fun `removed credential fails distinctly from a removed key`() = runTest {
        val failure = assertFailsWith<HolderKeyBindingException> {
            Wallet(id = "wallet").resolveHolderKey("missing-credential", setOf(KeyUsage.SIGN))
        }

        assertEquals(HolderKeyBindingErrorCode.CREDENTIAL_NOT_FOUND, failure.code)
    }

    @Test
    fun `import binds only one matching Crypto2 signing key`() = runTest {
        val holderKey = signingKey("holder")
        val credential = mdocCredential(holderKey)
        val uniqueStore = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }

        val bound = Wallet(id = "unique", keyStores = listOf(uniqueStore))
            .withImportedHolderKeyBinding(credential)
        val missing = Wallet(id = "missing")
            .withImportedHolderKeyBinding(bound)
        val duplicate = Wallet(
            id = "duplicate",
            keyStores = listOf(
                InMemoryKeyStore().apply { addCrypto2Key(holderKey) },
                InMemoryKeyStore().apply { addCrypto2Key(holderKey) },
            ),
        ).withImportedHolderKeyBinding(bound)

        assertEquals(HolderKeyBindingOrigin.IMPORT, assertNotNull(bound.holderKeyBinding).origin)
        assertNull(missing.holderKeyBinding)
        assertNull(duplicate.holderKeyBinding)
    }

    @Test
    fun `import separates public identity discovery from signing lookup`() = runTest {
        val holderKey = signingKey("holder")
        val keyStore = UsageRequiringKeyStore(holderKey)
        val wallet = Wallet(id = "wallet", keyStores = listOf(keyStore))

        val bound = wallet.withImportedHolderKeyBinding(mdocCredential(holderKey))

        assertEquals(HolderKeyBindingOrigin.IMPORT, assertNotNull(bound.holderKeyBinding).origin)
        assertEquals(1, keyStore.publicKeyLookups)
        assertEquals(listOf(setOf(KeyUsage.SIGN)), keyStore.requestedUsages)
    }

    @Test
    fun `import does not hide key provider failures as an unbound credential`() = runTest {
        val holderKey = signingKey("holder")
        val wallet = Wallet(
            id = "wallet",
            keyStores = listOf(FailingDiscoveryKeyStore(holderKey.id.value)),
        )

        val failure = assertFailsWith<HolderKeyBindingException> {
            wallet.withImportedHolderKeyBinding(mdocCredential(holderKey))
        }

        assertEquals(HolderKeyBindingErrorCode.KEY_PROVIDER_UNAVAILABLE, failure.code)
    }

    @Test
    fun `import ignores unrelated keys that cannot sign`() = runTest {
        val verifyOnly = verifyOnlyKey("verify-only")
        val holderKey = signingKey("holder")
        val store = InMemoryKeyStore().apply {
            addCrypto2Key(verifyOnly)
            addCrypto2Key(holderKey)
        }
        val wallet = Wallet(id = "wallet", keyStores = listOf(store))

        val bound = wallet.withImportedHolderKeyBinding(mdocCredential(holderKey))

        assertEquals(holderKey.id.value, wallet.resolveHolderKey(bound, setOf(KeyUsage.SIGN)).keyMaterial.keyId)
    }

    @Test
    fun `credential import persists an unambiguous holder binding`() = runTest {
        val holderKey = signingKey("holder")
        val keyStore = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val credentialStore = InMemoryCredentialStore()
        val wallet = Wallet(
            id = "wallet",
            keyStores = listOf(keyStore),
            credentialStores = listOf(credentialStore),
        )
        val rawMdoc = mdocCredential(holderKey).credential.signed!!

        val imported = WalletCredentialHandler.importCredential(
            wallet,
            ImportCredentialRequest(rawCredential = rawMdoc),
        )

        assertEquals(HolderKeyBindingOrigin.IMPORT, imported.holderKeyBinding?.origin)
        assertEquals(imported.holderKeyBinding, credentialStore.getCredential(imported.id)?.holderKeyBinding)
    }

    @Test
    fun `deleted and replaced provider keys fail closed`() = runTest {
        val holderKey = signingKey("holder")
        val keyStore = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val wallet = Wallet(id = "wallet", keyStores = listOf(keyStore))
        val bound = wallet.withRequiredImportedHolderKeyBinding(mdocCredential(holderKey))

        keyStore.removeKey(holderKey.id.value)
        val missing = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(bound, setOf(KeyUsage.SIGN))
        }
        assertEquals(HolderKeyBindingErrorCode.KEY_NOT_FOUND, missing.code)

        keyStore.addCrypto2Key(signingKey(holderKey.id.value))
        val replaced = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(bound, setOf(KeyUsage.SIGN))
        }
        assertEquals(HolderKeyBindingErrorCode.KEY_DOES_NOT_MATCH_BINDING, replaced.code)
    }

    @Test
    fun `reordered key providers fail closed instead of selecting a same-id key`() = runTest {
        val holderKey = signingKey("shared-id")
        val unrelatedKey = signingKey("shared-id")
        val holderStore = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val unrelatedStore = InMemoryKeyStore().apply { addCrypto2Key(unrelatedKey) }
        val bound = Wallet(id = "wallet", keyStores = listOf(holderStore, unrelatedStore))
            .withVerifiedIssuanceHolderKeyBinding(
                credential = mdocCredential(holderKey),
                keyMaterial = holderStore.getKeyMaterial(holderKey.id.value, setOf(KeyUsage.SIGN))!!
                    .copy(keyReference = walletStoreKeyReference(0, holderKey.id.value)),
            )

        val failure = assertFailsWith<HolderKeyBindingException> {
            Wallet(id = "wallet", keyStores = listOf(unrelatedStore, holderStore))
                .resolveHolderKey(bound, setOf(KeyUsage.SIGN))
        }

        assertEquals(HolderKeyBindingErrorCode.KEY_DOES_NOT_MATCH_BINDING, failure.code)
    }

    @Test
    fun `unsupported binding contracts fail with stable reasons`() = runTest {
        val holderKey = signingKey("holder")
        val wallet = Wallet(
            id = "wallet",
            keyStores = listOf(InMemoryKeyStore().apply { addCrypto2Key(holderKey) }),
        )
        val bound = wallet.withRequiredImportedHolderKeyBinding(mdocCredential(holderKey))
        val binding = assertNotNull(bound.holderKeyBinding)

        val schemaFailure = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(
                bound.copy(holderKeyBinding = binding.copy(schemaVersion = 2)),
                setOf(KeyUsage.SIGN),
            )
        }
        val extractorFailure = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(
                bound.copy(holderKeyBinding = binding.copy(extractorVersion = 2)),
                setOf(KeyUsage.SIGN),
            )
        }
        val algorithmFailure = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(
                bound.copy(
                    holderKeyBinding = binding.copy(
                        publicKeyThumbprint = binding.publicKeyThumbprint.copy(algorithm = "sha256"),
                    )
                ),
                setOf(KeyUsage.SIGN),
            )
        }
        val referenceFailure = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(
                bound.copy(holderKeyBinding = binding.copy(keyReference = "unsupported")),
                setOf(KeyUsage.SIGN),
            )
        }

        assertEquals(HolderKeyBindingErrorCode.UNSUPPORTED_BINDING_VERSION, schemaFailure.code)
        assertEquals(HolderKeyBindingErrorCode.UNSUPPORTED_BINDING_VERSION, extractorFailure.code)
        assertEquals(HolderKeyBindingErrorCode.UNSUPPORTED_THUMBPRINT_ALGORITHM, algorithmFailure.code)
        assertEquals(HolderKeyBindingErrorCode.KEY_REFERENCE_INVALID, referenceFailure.code)
    }

    @Test
    fun `tampered binding and issuance mismatch are rejected`() = runTest {
        val holderKey = signingKey("holder")
        val otherKey = signingKey("other")
        val keyStore = InMemoryKeyStore().apply {
            addCrypto2Key(holderKey)
            addCrypto2Key(otherKey)
        }
        val wallet = Wallet(id = "wallet", keyStores = listOf(keyStore))
        val credential = mdocCredential(holderKey)
        val bound = wallet.withRequiredImportedHolderKeyBinding(credential)
        val otherBound = wallet.withRequiredImportedHolderKeyBinding(
            mdocCredential(otherKey, id = "other-mdoc"),
        )

        val tampered = bound.copy(
            holderKeyBinding = bound.holderKeyBinding!!.copy(
                publicKeyThumbprint = otherBound.holderKeyBinding!!.publicKeyThumbprint,
            )
        )
        val bindingFailure = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(tampered, setOf(KeyUsage.SIGN))
        }
        assertEquals(HolderKeyBindingErrorCode.BINDING_DOES_NOT_MATCH_CREDENTIAL, bindingFailure.code)

        val issuanceFailure = assertFailsWith<HolderKeyBindingException> {
            wallet.withVerifiedIssuanceHolderKeyBinding(
                credential,
                wallet.resolveKeyMaterial(otherKey.id.value, setOf(KeyUsage.SIGN))!!,
            )
        }
        assertEquals(HolderKeyBindingErrorCode.BINDING_DOES_NOT_MATCH_CREDENTIAL, issuanceFailure.code)
    }

    @Test
    fun `mdoc presenter signs only with the MSO device key`() = runTest {
        val holderKey = signingKey("holder")
        val unrelatedKey = signingKey("unrelated")
        val credential = mdocCredential(holderKey).credential as MdocsCredential
        val namespaces = DeviceNameSpaces(emptyMap())
        val transcript = MdocPresenter.buildSessionTranscript(
            AuthorizationRequest(clientId = "verifier", nonce = "nonce"),
            "https://verifier.example/response",
            null,
        )

        val deviceAuth = MdocPresenter.buildDeviceAuth(transcript, credential, namespaces, holderKey)
        val signature = assertIs<DeviceAuth.Signature>(deviceAuth).signature
        val detachedPayload = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            transcript,
            credential.docType,
            ByteStringWrapper(namespaces),
        )

        assertEquals(Cose.Algorithm.ES256, signature.protectedAlgorithm())
        assertEquals(true, signature.verifyDetached(holderKey, detachedPayload, Cose.Algorithm.ES256))
        assertFailsWith<IllegalArgumentException> {
            MdocPresenter.buildDeviceAuth(transcript, credential, namespaces, unrelatedKey)
        }
    }

    @Test
    fun `holder key authorization failure propagates without default-key fallback`() = runTest {
        val holderMaterial = signingKey("protected-holder")
        val authorizationFailure = IllegalStateException("holder authorization denied")
        var protectedSignCalls = 0
        val protectedHolder = object : id.walt.crypto2.keys.Key {
            override val id = holderMaterial.id
            override val spec = holderMaterial.spec
            override val usages = holderMaterial.usages
            override val capabilities = holderMaterial.capabilities.copy(
                signer = Signer { _, _ ->
                    protectedSignCalls++
                    throw authorizationFailure
                },
                privateKeyExporter = null,
            )
        }
        val unrelatedDefault = signingKey("unrelated-default")
        val keyStore = InMemoryKeyStore().apply {
            addCrypto2Key(unrelatedDefault)
            addCrypto2Key(protectedHolder)
        }
        val wallet = Wallet(id = "wallet", keyStores = listOf(keyStore))
        val bound = wallet.withRequiredImportedHolderKeyBinding(mdocCredential(protectedHolder))
        val resolved = wallet.resolveHolderKey(bound, setOf(KeyUsage.SIGN)).keyMaterial.requireCrypto2Key()
        val transcript = MdocPresenter.buildSessionTranscript(
            AuthorizationRequest(clientId = "verifier", nonce = "nonce"),
            "https://verifier.example/response",
            null,
        )

        val failure = assertFailsWith<IllegalStateException> {
            MdocPresenter.buildDeviceAuth(
                transcript,
                bound.credential as MdocsCredential,
                DeviceNameSpaces(emptyMap()),
                resolved,
            )
        }

        assertSame(authorizationFailure, failure)
        assertEquals(1, protectedSignCalls)
    }

    @Test
    fun `unsupported usage and provider failure retain precise errors`() = runTest {
        val verifyOnlyKey = verifyOnlyKey("verify-only")
        val verifyOnlyStore = InMemoryKeyStore().apply { addCrypto2Key(verifyOnlyKey) }
        val verifyOnlyWallet = Wallet(id = "verify", keyStores = listOf(verifyOnlyStore))
        val verifyOnlyCredential = mdocCredential(verifyOnlyKey)

        val usageFailure = assertFailsWith<HolderKeyBindingException> {
            verifyOnlyWallet.withRequiredImportedHolderKeyBinding(verifyOnlyCredential)
        }
        assertEquals(HolderKeyBindingErrorCode.KEY_USAGE_UNSUPPORTED, usageFailure.code)

        val signingKey = signingKey("provider-key")
        val healthyStore = InMemoryKeyStore().apply { addCrypto2Key(signingKey) }
        val healthyWallet = Wallet(id = "healthy", keyStores = listOf(healthyStore))
        val bound = healthyWallet.withRequiredImportedHolderKeyBinding(mdocCredential(signingKey))
        val failingWallet = Wallet(id = "failing", keyStores = listOf(FailingKeyStore()))

        val providerFailure = assertFailsWith<HolderKeyBindingException> {
            failingWallet.resolveHolderKey(bound, setOf(KeyUsage.SIGN))
        }
        assertEquals(HolderKeyBindingErrorCode.KEY_PROVIDER_UNAVAILABLE, providerFailure.code)
    }

    @Test
    fun `MAC-only holder key can be bound and resolved for key agreement`() = runTest {
        val holderKey = keyAgreementKey("mac-holder")
        val store = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val wallet = Wallet(id = "mac-wallet", keyStores = listOf(store))

        val bound = wallet.withRequiredImportedHolderKeyBinding(mdocCredential(holderKey))
        val resolved = wallet.resolveHolderKey(bound, setOf(KeyUsage.KEY_AGREEMENT))

        assertSame(holderKey, resolved.keyMaterial.crypto2Key)
        val signatureFailure = assertFailsWith<HolderKeyBindingException> {
            wallet.resolveHolderKey(bound, setOf(KeyUsage.SIGN))
        }
        assertEquals(HolderKeyBindingErrorCode.KEY_USAGE_UNSUPPORTED, signatureFailure.code)
    }

    @Test
    fun `remote presentation matching excludes a MAC-only holder key`() = runTest {
        val holderKey = keyAgreementKey("mac-holder")
        val credentialStore = InMemoryCredentialStore()
        val wallet = Wallet(
            id = "mac-wallet",
            keyStores = listOf(InMemoryKeyStore().apply { addCrypto2Key(holderKey) }),
            credentialStores = listOf(credentialStore),
        )
        credentialStore.addCredential(
            wallet.withRequiredImportedHolderKeyBinding(mdocCredential(holderKey)),
        )

        val failure = assertFailsWith<HolderKeyBindingException> {
            WalletPresentationHandler.matchCredentialsFromStore(
                wallet,
                MatchCredentialsFromStoreRequest(
                    DcqlQuery(credentials = listOf(mdocQuery("mdl", MDOC_DOCTYPE))),
                ),
            )
        }

        assertEquals(HolderKeyBindingErrorCode.KEY_USAGE_UNSUPPORTED, failure.code)
    }

    @Test
    fun `provider argument failures are not mislabeled as unsupported usage`() = runTest {
        val holderKey = signingKey("holder")
        val healthyStore = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val bound = Wallet(id = "healthy", keyStores = listOf(healthyStore))
            .withRequiredImportedHolderKeyBinding(mdocCredential(holderKey))

        val failure = assertFailsWith<HolderKeyBindingException> {
            Wallet(id = "corrupt", keyStores = listOf(CorruptOperationalKeyStore()))
                .resolveHolderKey(bound, setOf(KeyUsage.SIGN))
        }

        assertEquals(HolderKeyBindingErrorCode.KEY_PROVIDER_UNAVAILABLE, failure.code)
    }

    private suspend fun signingKey(id: String) = key(id, setOf(KeyUsage.SIGN, KeyUsage.VERIFY))

    private suspend fun keyAgreementKey(id: String) = key(id, setOf(KeyUsage.KEY_AGREEMENT))

    private suspend fun verifyOnlyKey(id: String): id.walt.crypto2.keys.Key {
        val material = signingKey(id)
        return object : id.walt.crypto2.keys.Key {
            override val id = material.id
            override val spec = material.spec
            override val usages = setOf(KeyUsage.VERIFY)
            override val capabilities = material.capabilities.copy(
                signer = null,
                digestSigner = null,
                privateKeyExporter = null,
            )
        }
    }

    private suspend fun key(id: String, usages: Set<KeyUsage>) =
        CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = usages,
            )
        )

    private suspend fun mdocCredential(
        holderKey: id.walt.crypto2.keys.Key,
        id: String = "mdoc",
        docType: String = MDOC_DOCTYPE,
    ): StoredCredential {
        val issuerKey = signingKey("issuer-$id")
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuerKey,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) {
            subjectDn = "CN=Holder key binding test issuer"
        }
        val holderPublicJwk = holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = holderPublicJwk.toCoseKey(),
            docType = docType,
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(MDOC_NAMESPACE to JsonObject(mapOf("given_name" to JsonPrimitive("Inga"))))
            ),
        )
        val raw = coseCompliantCbor.encodeToByteArray(
            Document.serializer(),
            Document(docType = docType, issuerSigned = issuerSigned),
        ).encodeToBase64Url()
        return StoredCredential(
            id = id,
            credential = CredentialParser.detectAndParse(raw).second,
            label = "mDL",
        )
    }

    private suspend fun mdocMatchingFixture(): MdocMatchingFixture {
        val unboundKey = signingKey("unbound-holder")
        val boundKey = signingKey("bound-holder")
        val credentialStore = InMemoryCredentialStore()
        val wallet = Wallet(
            id = "matching-wallet",
            keyStores = listOf(InMemoryKeyStore().apply {
                addCrypto2Key(unboundKey)
                addCrypto2Key(boundKey)
            }),
            credentialStores = listOf(credentialStore),
        )
        val boundCredential = wallet.withImportedHolderKeyBinding(
            mdocCredential(boundKey, id = "bound-pid", docType = PID_DOCTYPE),
        )
        credentialStore.addCredential(mdocCredential(unboundKey, id = "unbound-mdl"))
        credentialStore.addCredential(boundCredential)
        return MdocMatchingFixture(wallet, boundCredential)
    }

    private data class MdocMatchingFixture(
        val wallet: Wallet,
        val boundCredential: StoredCredential,
    )

    private class FailingKeyStore : WalletKeyStore {
        override suspend fun getKey(keyId: String) = null
        override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>) =
            error("provider unavailable")

        override suspend fun listKeys(): Flow<WalletKeyInfo> = emptyFlow()
        override suspend fun addKey(key: id.walt.crypto.keys.Key): String = error("not used")
        override suspend fun removeKey(keyId: String): Boolean = false
    }

    private class FailingDiscoveryKeyStore(
        private val keyId: String,
    ) : WalletKeyStore {
        override suspend fun getKey(keyId: String) = null
        override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>) = null
        override suspend fun getPublicKeyMaterial(keyId: String): WalletPublicKeyMaterial =
            error("provider unavailable")

        override suspend fun listKeys(): Flow<WalletKeyInfo> = kotlinx.coroutines.flow.flowOf(
            WalletKeyInfo(keyId, "test"),
        )

        override suspend fun addKey(key: id.walt.crypto.keys.Key): String = error("not used")
        override suspend fun removeKey(keyId: String): Boolean = false
    }

    private class CorruptOperationalKeyStore : WalletKeyStore {
        override suspend fun getKey(keyId: String) = null
        override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): id.walt.crypto2.keys.Key =
            throw IllegalArgumentException("stored provider descriptor is corrupt")

        override suspend fun listKeys(): Flow<WalletKeyInfo> = emptyFlow()
        override suspend fun addKey(key: id.walt.crypto.keys.Key): String = error("not used")
        override suspend fun removeKey(keyId: String): Boolean = false
    }

    private class UsageRequiringKeyStore(
        private val key: id.walt.crypto2.keys.Key,
    ) : WalletKeyStore {
        val requestedUsages = mutableListOf<Set<KeyUsage>>()
        var publicKeyLookups = 0

        override suspend fun getKey(keyId: String) = null

        override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): id.walt.crypto2.keys.Key? {
            requestedUsages += usages
            if (usages.isEmpty()) throw WalletKeyUsageUnsupportedException("key usages must not be empty")
            if (keyId != key.id.value) return null
            if (!usages.all(key.usages::contains)) {
                throw WalletKeyUsageUnsupportedException("key does not permit requested usages")
            }
            return key
        }

        override suspend fun getPublicKeyMaterial(keyId: String): WalletPublicKeyMaterial? {
            publicKeyLookups++
            return key.takeIf { keyId == key.id.value }
                ?.exportPublicJwk()
                ?.let(::WalletPublicKeyMaterial)
        }

        override suspend fun listKeys(): Flow<WalletKeyInfo> = kotlinx.coroutines.flow.flowOf(
            WalletKeyInfo(key.id.value, key.spec.toString()),
        )

        override suspend fun addKey(key: id.walt.crypto.keys.Key): String = error("not used")
        override suspend fun removeKey(keyId: String): Boolean = false
    }

    private companion object {
        const val MDOC_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
        const val MDOC_NAMESPACE = "org.iso.18013.5.1"

        fun mdocQuery(id: String, docType: String) = CredentialQuery(
            id = id,
            format = CredentialFormat.MSO_MDOC,
            meta = MsoMdocMeta(doctypeValue = docType),
        )
    }
}
