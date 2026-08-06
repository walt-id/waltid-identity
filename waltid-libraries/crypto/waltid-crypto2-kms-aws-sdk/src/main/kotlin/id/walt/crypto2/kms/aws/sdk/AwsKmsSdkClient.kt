package id.walt.crypto2.kms.aws.sdk

import aws.sdk.kotlin.services.kms.KmsClient
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
import aws.sdk.kotlin.services.kms.model.ReplicateKeyRequest
import aws.sdk.kotlin.services.kms.model.ReplicateKeyResponse
import aws.sdk.kotlin.services.kms.model.ScheduleKeyDeletionRequest
import aws.sdk.kotlin.services.kms.model.ScheduleKeyDeletionResponse
import aws.sdk.kotlin.services.kms.model.SignRequest
import aws.sdk.kotlin.services.kms.model.SignResponse
import aws.sdk.kotlin.services.kms.model.VerifyRequest
import aws.sdk.kotlin.services.kms.model.VerifyResponse

interface AwsKmsSdkClient : AutoCloseable {
    suspend fun createKey(request: CreateKeyRequest): CreateKeyResponse
    suspend fun createAlias(request: CreateAliasRequest): CreateAliasResponse
    suspend fun replicateKey(request: ReplicateKeyRequest): ReplicateKeyResponse
    suspend fun getPublicKey(request: GetPublicKeyRequest): GetPublicKeyResponse
    suspend fun sign(request: SignRequest): SignResponse
    suspend fun verify(request: VerifyRequest): VerifyResponse
    suspend fun encrypt(request: EncryptRequest): EncryptResponse
    suspend fun decrypt(request: DecryptRequest): DecryptResponse
    suspend fun scheduleKeyDeletion(request: ScheduleKeyDeletionRequest): ScheduleKeyDeletionResponse
}

fun interface AwsKmsSdkClientFactory {
    fun create(region: String): AwsKmsSdkClient
}

class DefaultAwsKmsSdkClientFactory : AwsKmsSdkClientFactory {
    override fun create(region: String): AwsKmsSdkClient = DefaultAwsKmsSdkClient(KmsClient { this.region = region })
}

private class DefaultAwsKmsSdkClient(private val delegate: KmsClient) : AwsKmsSdkClient {
    override suspend fun createKey(request: CreateKeyRequest) = delegate.createKey(request)
    override suspend fun createAlias(request: CreateAliasRequest) = delegate.createAlias(request)
    override suspend fun replicateKey(request: ReplicateKeyRequest) = delegate.replicateKey(request)
    override suspend fun getPublicKey(request: GetPublicKeyRequest) = delegate.getPublicKey(request)
    override suspend fun sign(request: SignRequest) = delegate.sign(request)
    override suspend fun verify(request: VerifyRequest) = delegate.verify(request)
    override suspend fun encrypt(request: EncryptRequest) = delegate.encrypt(request)
    override suspend fun decrypt(request: DecryptRequest) = delegate.decrypt(request)
    override suspend fun scheduleKeyDeletion(request: ScheduleKeyDeletionRequest) = delegate.scheduleKeyDeletion(request)
    override fun close() = delegate.close()
}
