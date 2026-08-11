package id.walt.crypto2.kms.aws.sdk

import aws.sdk.kotlin.services.kms.model.CreateAliasRequest
import aws.sdk.kotlin.services.kms.model.CreateAliasResponse
import aws.sdk.kotlin.services.kms.model.CreateKeyRequest
import aws.sdk.kotlin.services.kms.model.CreateKeyResponse
import aws.sdk.kotlin.services.kms.model.DecryptRequest
import aws.sdk.kotlin.services.kms.model.DecryptResponse
import aws.sdk.kotlin.services.kms.model.EncryptRequest
import aws.sdk.kotlin.services.kms.model.EncryptResponse
import aws.sdk.kotlin.services.kms.model.GetPublicKeyRequest
import aws.sdk.kotlin.services.kms.model.GetPublicKeyResponse
import aws.sdk.kotlin.services.kms.model.KeyMetadata
import aws.sdk.kotlin.services.kms.model.ReplicateKeyRequest
import aws.sdk.kotlin.services.kms.model.ReplicateKeyResponse
import aws.sdk.kotlin.services.kms.model.ScheduleKeyDeletionRequest
import aws.sdk.kotlin.services.kms.model.ScheduleKeyDeletionResponse
import aws.sdk.kotlin.services.kms.model.SignRequest
import aws.sdk.kotlin.services.kms.model.SignResponse
import aws.sdk.kotlin.services.kms.model.VerifyRequest
import aws.sdk.kotlin.services.kms.model.VerifyResponse
import aws.smithy.kotlin.runtime.time.Instant as AwsInstant
import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSign
import id.walt.cose.verify
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class AwsKmsSdkKeyProviderTest {
    @Test
    fun `multi-region key survives restart and follows explicit failover and deletion order`() = runTest {
        val p1363 = ByteArray(64) { index -> if (index == 0 || index == 32) 0x80.toByte() else index.toByte() }
        val state = FakeState(EcdsaSignatureCodec.p1363ToDer(p1363, 32))
        val options = AwsKmsSdkOptions(
            primaryRegion = "us-east-1",
            alias = "wallet-signing",
            description = "Wallet signing key",
            tags = mapOf("service" to "wallet"),
            multiRegion = true,
            replicaRegions = listOf("eu-west-1"),
            failoverOrder = listOf("eu-west-1", "us-east-1"),
        )
        val runtime = runtime(state)
        val generated = runtime.generateManagedKey(
            AwsKmsSdkKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )
        assertEquals("alias/wallet-signing", state.aliasName)
        assertEquals(mapOf("service" to "wallet"), state.createdTags)
        assertEquals(listOf("eu-west-1"), state.replicaTargets)
        val providerData = generated.storedKey.providerData.toByteArray().decodeToString()
        assertFalse("service" in providerData)

        val restored = runtime(state).restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(generated.storedKey))
        )
        val signature = assertNotNull(restored.capabilities.signer).sign(
            "message".encodeToByteArray(),
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
        )
        assertContentEquals(p1363, signature)
        val jws = CompactJws.sign("jose".encodeToByteArray(), restored, JwsAlgorithm.ES256)
        assertEquals("jose", CompactJws.verify(jws, restored, JwsAlgorithm.ES256).payload.decodeToString())
        val cose = CoseSign1.createAndSign(
            CoseHeaders(algorithm = Cose.Algorithm.ES256),
            payload = "cose".encodeToByteArray(),
            key = restored,
        )
        assertTrue(cose.verify(restored, Cose.Algorithm.ES256))
        assertEquals(
            listOf("eu-west-1", "us-east-1", "eu-west-1", "us-east-1", "eu-west-1", "us-east-1"),
            state.signRegions,
        )
        assertEquals(
            KeyDeletionResult.Scheduled(Instant.parse("2026-07-25T00:00:00Z")),
            assertNotNull(restored.capabilities.deleter).delete(),
        )
        assertEquals(listOf("eu-west-1", "us-east-1"), state.deletionRegions)
        assertTrue(state.closeCount > 0)
    }

    @Test
    fun `alias failure schedules rollback and preserves original failure`() = runTest {
        val state = FakeState(ByteArray(64)).apply { aliasFailure = IllegalStateException("alias exists") }
        val failure = assertFailsWith<IllegalStateException> {
            runtime(state).generateManagedKey(
                AwsKmsSdkKeyProvider.ID,
                GenerateManagedKeyRequest(
                    id = KeyId("logical-key"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN),
                    providerOptions = AwsKmsSdkOptions(
                        primaryRegion = "us-east-1",
                        alias = "existing",
                    ).encode(),
                ),
            )
        }

        assertEquals("alias exists", failure.message)
        assertEquals(listOf("us-east-1"), state.deletionRegions)
    }

    @Test
    fun `RSA encryption uses provider-bound opaque ciphertext`() = runTest {
        val state = FakeState(ByteArray(0))
        val plaintext = "plaintext".encodeToByteArray()
        val runtime = runtime(state)
        val key = runtime.generateManagedKey(
            AwsKmsSdkKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("rsa-key"),
                spec = KeySpec.Rsa(2048),
                usages = setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT),
                providerOptions = AwsKmsSdkOptions(primaryRegion = "us-east-1").encode(),
            ),
        )
        state.decryptedPlaintext = plaintext
        val algorithm = AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256)
        val ciphertext = assertIs<AsymmetricCiphertext.Opaque>(
            assertNotNull(key.capabilities.encryptor).encrypt(plaintext, algorithm, null)
        )

        assertEquals(AwsKmsSdkKeyProvider.ID, ciphertext.provider)
        assertContentEquals(state.ciphertext, ciphertext.blob.toByteArray())
        assertContentEquals(plaintext, assertNotNull(key.capabilities.decryptor).decrypt(ciphertext, null))
    }

    private fun runtime(state: FakeState): CryptoRuntime = CryptoRuntime(
        softwareProviders = emptyList(),
        managedProviders = listOf(
            AwsKmsSdkKeyProvider(
                clientFactory = AwsKmsSdkClientFactory { region -> FakeClient(region, state) },
            )
        ),
    )

    private class FakeState(val signature: ByteArray) {
        val replicaTargets = mutableListOf<String>()
        val signRegions = mutableListOf<String>()
        val deletionRegions = mutableListOf<String>()
        var aliasName: String? = null
        var createdTags: Map<String, String> = emptyMap()
        var aliasFailure: Throwable? = null
        var closeCount = 0
        val ciphertext = ByteArray(256) { 8 }
        var decryptedPlaintext = byteArrayOf()
    }

    private class FakeClient(
        private val region: String,
        private val state: FakeState,
    ) : AwsKmsSdkClient {
        override suspend fun createKey(request: CreateKeyRequest): CreateKeyResponse {
            state.createdTags = request.tags.orEmpty().associate { requireNotNull(it.tagKey) to requireNotNull(it.tagValue) }
            return CreateKeyResponse {
                keyMetadata = KeyMetadata {
                    keyId = "mrk-remote"
                    keySpec = request.keySpec
                }
            }
        }

        override suspend fun createAlias(request: CreateAliasRequest): CreateAliasResponse {
            state.aliasName = request.aliasName
            state.aliasFailure?.let { throw it }
            return CreateAliasResponse {}
        }

        override suspend fun replicateKey(request: ReplicateKeyRequest): ReplicateKeyResponse {
            state.replicaTargets += requireNotNull(request.replicaRegion)
            return ReplicateKeyResponse {}
        }

        override suspend fun getPublicKey(request: GetPublicKeyRequest): GetPublicKeyResponse =
            GetPublicKeyResponse { publicKey = TEST_SPKI }

        override suspend fun sign(request: SignRequest): SignResponse {
            state.signRegions += region
            if (region == "eu-west-1") throw IOException("region unavailable")
            return SignResponse { signature = state.signature }
        }

        override suspend fun verify(request: VerifyRequest): VerifyResponse = VerifyResponse { signatureValid = true }
        override suspend fun encrypt(request: EncryptRequest): EncryptResponse =
            EncryptResponse { ciphertextBlob = state.ciphertext }

        override suspend fun decrypt(request: DecryptRequest): DecryptResponse =
            DecryptResponse { plaintext = state.decryptedPlaintext }

        override suspend fun scheduleKeyDeletion(request: ScheduleKeyDeletionRequest): ScheduleKeyDeletionResponse {
            state.deletionRegions += region
            val date = if (region == "eu-west-1") "2026-07-24T00:00:00Z" else "2026-07-25T00:00:00Z"
            return ScheduleKeyDeletionResponse { deletionDate = AwsInstant.fromIso8601(date) }
        }

        override fun close() {
            state.closeCount++
        }
    }

    companion object {
        private val TEST_SPKI = byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x01)
    }
}
