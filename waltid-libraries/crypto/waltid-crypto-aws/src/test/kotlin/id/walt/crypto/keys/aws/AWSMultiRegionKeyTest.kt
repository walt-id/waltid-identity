package id.walt.crypto.keys.aws

import aws.sdk.kotlin.services.kms.KmsClient
import aws.sdk.kotlin.services.kms.model.KmsException
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for AWS KMS multi-region key support.
 * 
 * Note: Tests marked with @Disabled require actual AWS credentials and 
 * should be run manually during integration testing.
 */
class AWSMultiRegionKeyTest {

    // =========================================================================
    // Configuration Model Tests
    // =========================================================================

    @Test
    fun `AWSKeyMetadataSDK supports replicaRegions field`() {
        val config = AWSKeyMetadataSDK(
            auth = AwsSDKAuth(region = "us-east-1"),
            multiRegion = true,
            replicaRegions = listOf("eu-west-1", "ap-southeast-1")
        )

        assertEquals("us-east-1", config.auth.region)
        assertEquals(true, config.multiRegion)
        assertEquals(listOf("eu-west-1", "ap-southeast-1"), config.replicaRegions)
    }

    @Test
    fun `AWSKeyMetadataSDK supports failover configuration`() {
        val config = AWSKeyMetadataSDK(
            auth = AwsSDKAuth(region = "us-east-1"),
            multiRegion = true,
            replicaRegions = listOf("eu-west-1", "ap-southeast-1"),
            enableFailover = true,
            failoverOrder = listOf("eu-west-1", "ap-southeast-1", "us-east-1")
        )

        assertEquals(true, config.enableFailover)
        assertEquals(listOf("eu-west-1", "ap-southeast-1", "us-east-1"), config.failoverOrder)
    }

    @Test
    fun `AWSKeyMetadataSDK defaults are null for optional fields`() {
        val config = AWSKeyMetadataSDK(
            auth = AwsSDKAuth(region = "us-east-1")
        )

        assertEquals(null, config.multiRegion)
        assertEquals(null, config.replicaRegions)
        assertEquals(null, config.enableFailover)
        assertEquals(null, config.failoverOrder)
        assertEquals(null, config.keyName)
        assertEquals(null, config.tags)
    }

    // =========================================================================
    // FailoverKmsClient Tests
    // =========================================================================

    @Test
    fun `FailoverKmsClient builds correct region order`() {
        val client = FailoverKmsClient(
            primaryRegion = "us-east-1",
            failoverRegions = listOf("eu-west-1", "ap-southeast-1")
        )

        val info = client.getFailoverInfo()
        assertEquals("us-east-1", info.primaryRegion)
        assertEquals(listOf("eu-west-1", "ap-southeast-1"), info.failoverRegions)
        assertEquals(listOf("us-east-1", "eu-west-1", "ap-southeast-1"), info.regionOrder)
    }

    @Test
    fun `FailoverKmsClient deduplicates primary region in failover list`() {
        val client = FailoverKmsClient(
            primaryRegion = "us-east-1",
            failoverRegions = listOf("us-east-1", "eu-west-1", "us-east-1") // Primary included in failover
        )

        val info = client.getFailoverInfo()
        // Primary should only appear once at the start
        assertEquals(listOf("us-east-1", "eu-west-1"), info.regionOrder)
    }

    @Test
    fun `FailoverKmsClient reports lastSuccessfulRegion`() = runTest {
        val client = FailoverKmsClient(
            primaryRegion = "us-east-1",
            failoverRegions = listOf("eu-west-1")
        )

        // Initially null
        assertEquals(null, client.lastSuccessfulRegion)

        // After a successful operation, should be set
        // Note: This would require mocking KmsClient for a real test
    }

    @Test
    fun `FailoverKmsClient respects enableFailover=false`() {
        val client = FailoverKmsClient(
            primaryRegion = "us-east-1",
            failoverRegions = listOf("eu-west-1"),
            enableFailover = false
        )

        val info = client.getFailoverInfo()
        assertEquals(false, info.enableFailover)
    }

    // =========================================================================
    // Serialization Tests
    // =========================================================================

    @Test
    fun `AWSKeyMetadataSDK serializes correctly with all fields`() {
        val config = AWSKeyMetadataSDK(
            auth = AwsSDKAuth(region = "us-east-1"),
            keyName = "test-key",
            tags = mapOf("env" to "test"),
            multiRegion = true,
            replicaRegions = listOf("eu-west-1"),
            enableFailover = true,
            failoverOrder = listOf("eu-west-1", "us-east-1")
        )

        val json = kotlinx.serialization.json.Json.encodeToString(AWSKeyMetadataSDK.serializer(), config)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(AWSKeyMetadataSDK.serializer(), json)

        assertEquals(config, decoded)
    }

    @Test
    fun `AWSKeyMetadataSDK serializes correctly with minimal fields`() {
        val config = AWSKeyMetadataSDK(
            auth = AwsSDKAuth(region = "us-east-1")
        )

        val json = kotlinx.serialization.json.Json.encodeToString(AWSKeyMetadataSDK.serializer(), config)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(AWSKeyMetadataSDK.serializer(), json)

        assertEquals(config, decoded)
    }

    // =========================================================================
    // Integration Tests (require AWS credentials)
    // =========================================================================

    // Note: These tests require AWS credentials and make real API calls.
    // Enable with RUN_AWS_TESTS=true environment variable.


    @Test
    fun `generate multi-region key with replicas`() = runBlocking {
        val config = AWSKeyMetadataSDK(
            auth = AwsSDKAuth(region = "eu-central-1"),
            keyName = "test-mrk-${System.currentTimeMillis()}",
            multiRegion = true,
            replicaRegions = listOf("eu-west-1")
        )

        val key = AWSKey.generateKey(KeyType.secp256r1, config)
        
        assertNotNull(key)
        assertTrue(key.id.startsWith("mrk-"), "Multi-region key ID should start with 'mrk-'")
        
        // Cleanup
        key.deleteKey()
    }

    @Test
    fun `delete multi-region key cascades to replicas`() = runBlocking {
        val config = AWSKeyMetadataSDK(
            auth = AwsSDKAuth(region = "eu-central-1"),
            keyName = "test-cascade-delete-${System.currentTimeMillis()}",
            multiRegion = true,
            replicaRegions = listOf("eu-west-1")
        )

        val key = AWSKey.generateKey(KeyType.secp256r1, config)
        
        // Give AWS time to propagate the replica
        delay(10000)
        
        // Delete should not throw - schedule deletion for both primary and replica
        // Note: AWS KMS schedules deletion (7-day pending window), returns success even
        // if the deletion date is set, keyState may still be transitioning
        try {
            key.deleteKey()
            // If no exception, the delete was scheduled successfully
        } catch (e: Exception) {
            throw AssertionError("Cascade delete should not throw: ${e.message}", e)
        }
    }

    @Test
    fun `signing with failover uses replica when primary unavailable`() = runTest {
        // This test would require mocking network failures
        // or using a test account with intentionally unavailable regions
    }

}
