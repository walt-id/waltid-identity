package id.walt.wallet2.mobile

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.persistence.keys.*
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Fake native facts exercise the adapter and mobile pipeline, not physical authentication. */
class NativeScaPresentationAuthorizerTest {
    @Test
    fun biometricOnlyKeyUsesTheReviewedDcApiPathAndSignedProof() = runTest {
        for (evidence in listOf(KeyAuthorizationEvidence.NATIVE_ATTRIBUTES, KeyAuthorizationEvidence.CREATION_RECORD)) {
            val fixture = fixture()
            fixture.provider.facts = fixture.provider.facts.copy(authorizationEvidence = evidence)
            val preview = fixture.preview()
            val response = fixture.submit(preview)
            val vp = Json.parseToJsonElement(response.dataJson).jsonObject.getValue("vp_token")
                .jsonObject.getValue("payment").jsonArray.single().jsonPrimitive.content
            val verified = CompactJws.verify(vp.substringAfterLast('~'), fixture.key, JwsAlgorithm.ES256)
            val claims = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
            assertEquals("origin:https://verifier.example", claims["aud"]?.jsonPrimitive?.content)
            assertEquals("dc_api", claims["response_mode"]?.jsonPrimitive?.content)
            assertEquals(buildJsonArray {
                add(buildJsonObject { put("possession", "other") })
                add(buildJsonObject { put("inherence", "other") })
            }, claims["amr"])
            assertEquals(1, fixture.provider.inspections)
            assertEquals(1, fixture.signatures)
            assertFails { fixture.submit(preview) } // Successful reviewed request is single-use.
        }
    }

    @Test
    fun ambiguousReusableAndUnprotectedPoliciesAreRejectedBeforeSigning() = runTest {
        for (policy in listOf(
            KeyUseAuthorizationPolicy.None,
            KeyUseAuthorizationPolicy.BiometricAny,
            KeyUseAuthorizationPolicy.BiometricTimedReuse(10),
            KeyUseAuthorizationPolicy.DeviceCredential(),
            KeyUseAuthorizationPolicy.BiometricOrDeviceCredential(),
        )) {
            val fixture = fixture()
            fixture.provider.policy = policy
            val preview = fixture.preview()
            assertFailsWith<KeyUseAuthorizationException> { fixture.submit(preview) }
            assertEquals(0, fixture.signatures, "$policy must not sign")
            assertEquals(0, fixture.provider.inspections)
        }
    }

    @Test
    fun unknownOrUnsuitableNativeFactsCannotBecomeAuthenticationClaims() = runTest {
        val good = eligibleFacts()
        for (facts in listOf(
            PlatformKeyFacts(),
            good.copy(origin = KeyOrigin.IMPORTED),
            good.copy(protection = KeyProtectionLevel.SOFTWARE),
            good.copy(securityLevel = KeySecurityLevel.UNKNOWN),
            good.copy(authorizationEvidence = KeyAuthorizationEvidence.UNKNOWN),
        )) {
            val fixture = fixture()
            fixture.provider.facts = facts
            val preview = fixture.preview()
            assertFailsWith<KeyUseAuthorizationException> { fixture.submit(preview) }
            assertEquals(0, fixture.signatures)
        }
    }

    @Test
    fun cancellationOrNativeFailureProducesNoResponseAndRetryRechecksEvidence() = runTest {
        val fixture = fixture()
        val preview = fixture.preview()
        fixture.signingFailure = CancellationException("Native authentication cancelled")
        assertFailsWith<CancellationException> { fixture.submit(preview) }
        assertEquals(1, fixture.provider.inspections)
        fixture.signingFailure = IllegalStateException("Native key disappeared")
        assertFailsWith<IllegalStateException> { fixture.submit(preview) }
        assertEquals(2, fixture.provider.inspections)
        fixture.signingFailure = null
        fixture.submit(preview)
        assertEquals(3, fixture.provider.inspections)
        assertEquals(3, fixture.signatures)
    }

    @Test
    fun ordinaryReviewedSubmissionAlsoUsesTheNativeEvidenceGate() = runTest {
        val fixture = fixture()
        fixture.provider.policy = KeyUseAuthorizationPolicy.None
        val url = URLBuilder("openid4vp://authorize").apply {
            fixture.request.forEach { (name, value) ->
                parameters.append(name, if (value is JsonPrimitive) value.content else value.toString())
            }
            parameters["response_mode"] = "direct_post"
            parameters["client_id"] = "redirect_uri:https://verifier.example/response"
            parameters["response_uri"] = "https://verifier.example/response"
        }.buildString()
        val preview = assertIs<MobileWalletPresentationPreviewResult.Ready>(fixture.wallet.previewPresentation(url)).preview
        assertFailsWith<KeyUseAuthorizationException> {
            fixture.wallet.submitPresentation(preview.previewHandle, preview.credentialOptions.map {
                MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId)
            })
        }
        assertEquals(0, fixture.signatures)
    }

    private class Fixture(val key: Key, val provider: FakeProvider, val request: JsonObject) {
        lateinit var wallet: MobileWallet
        var signatures = 0
        var signingFailure: Throwable? = null
        suspend fun preview(): MobileWalletDigitalCredentialPreview = wallet.previewDigitalCredentialPresentation(
            MobileWalletDigitalCredentialRequest(
                protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                dataJson = request.toString(), verifiedOrigin = "https://verifier.example",
            ),
        )
        suspend fun submit(preview: MobileWalletDigitalCredentialPreview): MobileWalletDigitalCredentialResponse =
            wallet.submitDigitalCredentialPresentation(preview.requestId, preview.credentialOptions.map {
                MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId)
            })
    }

    private suspend fun fixture(): Fixture {
        val software = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId("synthetic-native-holder"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
        )
        val descriptor = StoredKey.Managed(
            StoredKey.CURRENT_VERSION, software.id, software.spec, software.usages,
            ProviderId("synthetic-platform"), 1, BinaryData(byteArrayOf(1)),
            requireNotNull(software.capabilities.publicKeyExporter).exportPublicKey(),
        )
        lateinit var fixture: Fixture
        val key = object : ManagedKey {
            override val storedKey = descriptor
            override val capabilities = software.capabilities.copy(
                privateKeyExporter = null,
                signer = Signer { data, algorithm ->
                    fixture.signatures++
                    fixture.signingFailure?.let { throw it }
                    requireNotNull(software.capabilities.signer).sign(data, algorithm)
                },
            )
        }
        val provider = FakeProvider()
        val walletFixture = scaWalletFixture(key, provider)
        fixture = Fixture(key, provider, walletFixture.request)
        fixture.wallet = walletFixture.wallet
        return fixture
    }

    private class FakeProvider : PlatformManagedKeyProvider {
        var policy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet
        var facts = eligibleFacts()
        var inspections = 0
        override fun keyUseAuthorizationPolicy(stored: StoredKey.Managed) = policy
        override suspend fun keyFacts(stored: StoredKey.Managed): PlatformKeyFacts { inspections++; return facts }
        override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport = error("Not used")
        override suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey = error("Not used")
        override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration = error("Not used")
        override suspend fun deleteManagedKey(stored: StoredKey.Managed) = error("Not used")
    }

    private companion object {
        fun eligibleFacts() = PlatformKeyFacts(
            origin = KeyOrigin.GENERATED, securityLevel = KeySecurityLevel.SECURE_ENCLAVE,
            protection = KeyProtectionLevel.HARDWARE, authorizationEvidence = KeyAuthorizationEvidence.CREATION_RECORD,
        )
    }
}
