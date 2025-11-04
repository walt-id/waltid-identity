# Azure Key Vault Integration Guide

## Overview

This guide explains how to integrate Azure Key Vault with walt.id crypto library using the centralized credential provider pattern.

- Client secret auth (App Registration) is supported
- Workload Identity / Managed Identity is supported via a token supplier (no secrets)

## Quick Start

### 1. Register Credentials at Application Startup

```kotlin
import id.walt.crypto.keys.azure.AzureAuth
import id.walt.crypto.keys.azure.AzureCredentialProvider

fun initializeAzureCredentials() {
    AzureCredentialProvider.register(
        vaultUrl = "https://your-vault.vault.azure.net",
        auth = AzureAuth(
            clientId = System.getenv("AZURE_CLIENT_ID")!!,
            clientSecret = System.getenv("AZURE_CLIENT_SECRET")!!,
            tenantId = System.getenv("AZURE_TENANT_ID")!!,
            keyVaultUrl = "https://your-vault.vault.azure.net"
        )
    )
}

// Call this before any Azure keys are used
fun main() {
    initializeAzureCredentials()
    startApplication()
}
```

### 2. Create Azure Keys

```kotlin
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.azure.AzureKey
import id.walt.crypto.keys.azure.AzureKeyMetadata

suspend fun createKey(): AzureKey {
    val metadata = AzureKeyMetadata(
        auth = AzureAuth(
            clientId = System.getenv("AZURE_CLIENT_ID")!!,
            clientSecret = System.getenv("AZURE_CLIENT_SECRET")!!,
            tenantId = System.getenv("AZURE_TENANT_ID")!!,
            keyVaultUrl = "https://your-vault.vault.azure.net"
        ),
        name = "my-key-name" // optional
    )
    
    // Key will automatically use provider credentials
    return AzureKey.generate(KeyType.secp256r1, metadata)
}
```

### 3. Use Keys

```kotlin
import id.walt.crypto.keys.KeySerialization

suspend fun signData(serializedKey: String, data: ByteArray): ByteArray {
    // Deserialize key
    val key = KeySerialization.deserializeKey(serializedKey).getOrThrow()
    
    // Initialize (automatically uses provider credentials)
    key.init()
    
    // Sign
    return key.signRaw(data)
}
```

## Configuration Management

### Environment Variables

```bash
export AZURE_VAULT_URL="https://your-vault.vault.azure.net"
export AZURE_CLIENT_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
export AZURE_CLIENT_SECRET="your-client-secret-value"
export AZURE_TENANT_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```

### Multiple Vaults

```kotlin
// Register credentials for multiple vaults
fun initializeMultipleVaults() {
    val vaults = mapOf(
        "https://vault1.vault.azure.net" to AzureAuth(
            clientId = System.getenv("VAULT1_CLIENT_ID")!!,
            clientSecret = System.getenv("VAULT1_CLIENT_SECRET")!!,
            tenantId = System.getenv("VAULT1_TENANT_ID")!!,
            keyVaultUrl = "https://vault1.vault.azure.net"
        ),
        "https://vault2.vault.azure.net" to AzureAuth(
            clientId = System.getenv("VAULT2_CLIENT_ID")!!,
            clientSecret = System.getenv("VAULT2_CLIENT_SECRET")!!,
            tenantId = System.getenv("VAULT2_TENANT_ID")!!,
            keyVaultUrl = "https://vault2.vault.azure.net"
        )
    )
    
    AzureCredentialProvider.registerAll(vaults)
}
```

### Configuration File Integration

```kotlin
// Using Typesafe Config (HOCON)
fun loadFromConfig() {
    val config = ConfigFactory.load()
    val vaultUrl = config.getString("azure.vaultUrl")
    
    AzureCredentialProvider.register(
        vaultUrl = vaultUrl,
        auth = AzureAuth(
            clientId = config.getString("azure.clientId"),
            clientSecret = config.getString("azure.clientSecret"),
            tenantId = config.getString("azure.tenantId"),
            keyVaultUrl = vaultUrl
        )
    )
}
```

Example `application.conf`:

```hocon
azure {
    vaultUrl = ${AZURE_VAULT_URL}
    clientId = ${AZURE_CLIENT_ID}
    clientSecret = ${AZURE_CLIENT_SECRET}
    tenantId = ${AZURE_TENANT_ID}
}
```

## Client Secret Rotation

### Zero-Downtime Rotation Process

1. **Create new client secret** in Azure Portal (keep old secret active)

2. **Update credentials** in your configuration:
   ```bash
   export AZURE_CLIENT_SECRET="NEW_SECRET_VALUE"
   ```

3. **Restart application** - credentials are reloaded on startup:
   ```bash
   ./restart-application.sh
   ```

4. **Verify** key operations work with new credentials

5. **Delete old secret** in Azure Portal

### Programmatic Rotation

```kotlin
fun rotateCredentials(vaultUrl: String, newClientSecret: String) {
    val currentAuth = AzureCredentialProvider.getCredentials(vaultUrl)
        ?: error("No credentials registered for $vaultUrl")
    
    val newAuth = currentAuth.copy(clientSecret = newClientSecret)
    
    AzureCredentialProvider.update(vaultUrl, newAuth)
    
    logger.info("Credentials updated. Restart application to apply changes.")
}
```

## API Reference

### AzureCredentialProvider

```kotlin
object AzureCredentialProvider {
    // Register credentials for a vault
    fun register(vaultUrl: String, auth: AzureAuth)
    
    // Register multiple vaults at once
    fun registerAll(credentials: Map<String, AzureAuth>)
    
    // Get credentials for a vault (returns null if not found)
    fun getCredentials(vaultUrl: String): AzureAuth?
    
    // Update credentials (for rotation)
    fun update(vaultUrl: String, auth: AzureAuth)
    
    // Remove credentials
    fun unregister(vaultUrl: String): Boolean
    
    // Check if credentials are registered
    fun hasCredentials(vaultUrl: String): Boolean
    
    // Get list of registered vaults
    fun getRegisteredVaults(): List<String>
    
    // Clear all registered credentials
    fun clear()

    // Workload/Managed Identity: register a token supplier
    // The supplier must return a bearer token and expiry for the given scope
    fun registerTokenSupplier(vaultUrl: String, supplier: suspend (scope: String) -> AzureToken)
    fun getTokenSupplier(vaultUrl: String): (suspend (scope: String) -> AzureToken)?
    fun unregisterTokenSupplier(vaultUrl: String): Boolean
    fun hasTokenSupplier(vaultUrl: String): Boolean
}
```

### How It Works

When you use an `AzureKey`, the credential resolution follows this order:

1. **Token supplier** (preferred) - Workload/Managed Identity (no secrets)
2. **Provider client-secret** - Centralized App Registration credentials
3. **Embedded auth** - Backward compatibility with existing keys
4. **Throw error** - If none available

This means:
- New keys created with registered provider credentials don't store credentials in the database
- Existing keys with embedded credentials continue to work
- No migration required for existing keys

## Complete Example

```kotlin
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.azure.*
import io.ktor.server.application.*

fun Application.configureAzure() {
    // 1. Initialize credentials at startup
    val vaultUrl = environment.config.property("azure.vaultUrl").getString()
    val clientId = environment.config.property("azure.clientId").getString()
    val clientSecret = environment.config.property("azure.clientSecret").getString()
    val tenantId = environment.config.property("azure.tenantId").getString()
    
    AzureCredentialProvider.register(
        vaultUrl = vaultUrl,
        auth = AzureAuth(clientId, clientSecret, tenantId, vaultUrl)
    )
    
    log.info("Azure Key Vault initialized: $vaultUrl")
}

// 2. Create keys
suspend fun createUserKey(userId: String): String {
    val metadata = AzureKeyMetadata(
        auth = AzureAuth(
            clientId = System.getenv("AZURE_CLIENT_ID")!!,
            clientSecret = System.getenv("AZURE_CLIENT_SECRET")!!,
            tenantId = System.getenv("AZURE_TENANT_ID")!!,
            keyVaultUrl = System.getenv("AZURE_VAULT_URL")!!
        ),
        name = "user-$userId"
    )
    
    val key = AzureKey.generate(KeyType.secp256r1, metadata)
    val serialized = KeySerialization.serializeKey(key)
    
    // Store serialized key in database
    database.saveUserKey(userId, serialized)
    
    return key.getKeyId()
}

// 3. Use keys
suspend fun signDocument(userId: String, document: ByteArray): ByteArray {
    // Load from database
    val serializedKey = database.getUserKey(userId)
    
    // Deserialize and initialize (uses provider credentials)
    val key = KeySerialization.deserializeKey(serializedKey).getOrThrow()
    key.init()
    
    // Sign
    return key.signRaw(document)
}
```

## Best Practices

1. **Initialize early** - Register credentials before any Azure keys are used
2. **Use environment variables** - Never hardcode secrets
3. **One registration per vault** - Register each vault URL once at startup
4. **Restart for rotation** - After updating credentials, restart the application
5. **Monitor expiration** - Set up alerts for client secret expiration (Azure Portal)

## Workload Identity / Managed Identity (No Secrets)

Use Azure Workload Identity (AKS) or Managed Identity (VM/App Service) by registering a token supplier instead of client secrets. The supplier returns a bearer token for scope `https://vault.azure.net/.default`.

### 1) Register token supplier at startup

```kotlin
import id.walt.crypto.keys.azure.*
import kotlin.time.Instant

// Minimal token model is provided by the library
// data class AzureToken(val accessToken: String, val expiration: Instant)

suspend fun getVaultToken(scope: String): AzureToken {
    // Obtain a token using your platform SDK or IMDS and map it:
    // - AKS Workload Identity: DefaultAzureCredential
    // - App Service/VM Managed Identity: ManagedIdentityCredential or IMDS endpoint
    // Pseudo-code:
    val accessToken = obtainAccessTokenFromPlatform(scope) // platform-specific
    val expiry = obtainExpiryFromPlatform()                 // platform-specific
    return AzureToken(accessToken, expiry)
}

fun configureWorkloadIdentity() {
    AzureCredentialProvider.registerTokenSupplier(
        vaultUrl = "https://your-vault.vault.azure.net",
        supplier = ::getVaultToken
    )
}
```

### 2) Required Azure setup

- Enable System/User-assigned Managed Identity (App Service/VM) or Workload Identity (AKS)
- Grant the identity access to your Key Vault (RBAC: Key Crypto User or appropriate role)
- No client secrets are needed in the application

With this setup, `AzureKey` automatically prefers the token supplier, falls back to provider client-secret if present, and finally to embedded legacy auth.

## Troubleshooting

### Error: "No Azure credentials available for vault"

**Cause:** Credentials not registered with `AzureCredentialProvider`.

**Solution:** Call `AzureCredentialProvider.register()` at application startup before using any keys.

### Error: "Could not retrieve access token"

**Cause:** Invalid credentials or network issues.

**Solutions:**
1. Verify credentials in Azure Portal
2. Check client secret hasn't expired
3. Ensure service principal has Key Vault access permissions
4. Verify network connectivity to Azure

### Keys not using updated credentials after rotation

**Cause:** Application hasn't been restarted.

**Solution:** Restart the application to reload credentials from configuration.

---

## Legacy System (Deprecated)

### Previous Implementation

Previously, Azure credentials were embedded directly in each key:

```kotlin
// OLD METHOD (Deprecated)
val key = AzureKey(
    id = "https://vault.../keys/key-name",
    auth = AzureAuth(...), // Embedded credentials
    ...
)
```

This approach required updating every stored key when rotating client secrets, which was:
- ❌ Time-consuming (database updates for every key)
- ❌ Error-prone (risk of data corruption)
- ❌ Complex (required migration scripts)
- ❌ Disruptive (service downtime)

### Migration to New System

**Good news: No migration required!** The new system is fully backward compatible.

#### For Existing Keys

Existing keys with embedded credentials continue to work automatically. The library checks the credential provider first, then falls back to embedded credentials.

#### For New Keys

Simply register credentials with `AzureCredentialProvider` at startup. New keys will automatically use the provider instead of embedding credentials:

```kotlin
// At application startup
fun main() {
    // Register credentials with provider
    AzureCredentialProvider.register(
        vaultUrl = "https://your-vault.vault.azure.net",
        auth = AzureAuth(
            clientId = System.getenv("AZURE_CLIENT_ID")!!,
            clientSecret = System.getenv("AZURE_CLIENT_SECRET")!!,
            tenantId = System.getenv("AZURE_TENANT_ID")!!,
            keyVaultUrl = "https://your-vault.vault.azure.net"
        )
    )
    
    startApplication()
}
```

That's it! Your application now:
- ✅ Creates new keys without embedded credentials
- ✅ Supports easy client secret rotation
- ✅ Works with existing keys seamlessly
- ✅ Requires zero database migration

### Benefits of New System

| Aspect | Old System | New System |
|--------|-----------|------------|
| Credential Location | Embedded in each key | Centralized provider |
| Secret Rotation | Update all keys in DB | Update config + restart |
| Database Changes | Required | Not required |
| Risk Level | High | Low |
| Downtime | Required | Optional |
| Backward Compatible | N/A | ✅ 100% |

### Recommendation

**Start using the new system immediately** by registering credentials at application startup. There's no need to migrate existing keys - they will continue working. As you create new keys, they'll automatically use the provider, making future client secret rotations trivial.

