package id.walt.crypto.keys.azure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private val logger = KotlinLogging.logger { }

/**
 * Centralized credential provider for Azure Key Vault authentication.
 * 
 * This provider allows managing Azure credentials separately from individual keys,
 * enabling credential rotation without modifying stored keys.
 * 
 * Usage:
 * ```kotlin
 * // Register credentials at application startup
 * AzureCredentialProvider.register(
 *     vaultUrl = "https://my-vault.vault.azure.net",
 *     auth = AzureAuth(
 *         clientId = "...",
 *         clientSecret = "...",
 *         tenantId = "...",
 *         keyVaultUrl = "https://my-vault.vault.azure.net"
 *     )
 * )
 * 
 * // Keys will automatically use registered credentials
 * val key = AzureKey(
 *     id = "https://my-vault.vault.azure.net/keys/my-key",
 *     auth = null // No embedded auth needed
 * )
 * ```
 * 
 * Benefits:
 * - Centralized credential management
 * - Easy credential rotation (update once, affects all keys)
 * - Backward compatible with embedded credentials
 * - Credentials stored in configuration, not database
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object AzureCredentialProvider {
    
    private val credentialStore = mutableMapOf<String, AzureAuth>()
    private val tokenSupplierStore = mutableMapOf<String, suspend (scope: String) -> AzureToken>()
    
    /**
     * Registers credentials for a specific Azure Key Vault.
     * 
     * @param vaultUrl The Key Vault URL (e.g., https://my-vault.vault.azure.net)
     * @param auth Azure authentication credentials
     */
    fun register(vaultUrl: String, auth: AzureAuth) {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        logger.debug { "Registering credentials for vault: $normalizedUrl" }
        credentialStore[normalizedUrl] = auth
    }
    
    /**
     * Registers credentials for multiple vaults at once.
     * 
     * @param credentials Map of vault URL to authentication credentials
     */
    fun registerAll(credentials: Map<String, AzureAuth>) {
        credentials.forEach { (vaultUrl, auth) ->
            register(vaultUrl, auth)
        }
    }
    
    /**
     * Retrieves credentials for a specific vault URL.
     * Returns null if no credentials are registered for the vault.
     * 
     * @param vaultUrl The Key Vault URL or full key ID
     * @return Azure authentication credentials or null if not found
     */
    fun getCredentials(vaultUrl: String): AzureAuth? {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        return credentialStore[normalizedUrl]
    }
    
    /**
     * Registers a token supplier for a specific vault. This enables Workload Identity / Managed Identity
     * or any external mechanism to provide short-lived access tokens without client secrets.
     * The supplier MUST return a token for the requested scope (e.g., "https://vault.azure.net/.default").
     */
    fun registerTokenSupplier(
        vaultUrl: String,
        supplier: suspend (scope: String) -> AzureToken
    ) {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        logger.debug { "Registering token supplier for vault: $normalizedUrl" }
        tokenSupplierStore[normalizedUrl] = supplier
    }
    
    /**
     * Returns the token supplier for a vault, if registered.
     */
    fun getTokenSupplier(vaultUrl: String): (suspend (scope: String) -> AzureToken)? {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        return tokenSupplierStore[normalizedUrl]
    }
    
    /**
     * Removes the token supplier for a vault.
     */
    fun unregisterTokenSupplier(vaultUrl: String): Boolean {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        val removed = tokenSupplierStore.remove(normalizedUrl) != null
        if (removed) logger.debug { "Unregistered token supplier for vault: $normalizedUrl" }
        return removed
    }
    
    /**
     * Checks whether a token supplier is registered for a vault.
     */
    fun hasTokenSupplier(vaultUrl: String): Boolean {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        return tokenSupplierStore.containsKey(normalizedUrl)
    }
    
    /**
     * Updates credentials for a specific vault.
     * This is the primary method for credential rotation.
     * 
     * @param vaultUrl The Key Vault URL
     * @param auth New Azure authentication credentials
     * @throws IllegalStateException if no credentials are currently registered for the vault
     */
    fun update(vaultUrl: String, auth: AzureAuth) {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        require(credentialStore.containsKey(normalizedUrl)) {
            "No credentials registered for vault: $normalizedUrl. Use register() first."
        }
        logger.info { "Updating credentials for vault: $normalizedUrl" }
        credentialStore[normalizedUrl] = auth
    }
    
    /**
     * Removes credentials for a specific vault.
     * 
     * @param vaultUrl The Key Vault URL
     * @return true if credentials were removed, false if none were registered
     */
    fun unregister(vaultUrl: String): Boolean {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        val removed = credentialStore.remove(normalizedUrl) != null
        if (removed) {
            logger.debug { "Unregistered credentials for vault: $normalizedUrl" }
        }
        return removed
    }
    
    /**
     * Checks if credentials are registered for a specific vault.
     * 
     * @param vaultUrl The Key Vault URL
     * @return true if credentials are registered
     */
    fun hasCredentials(vaultUrl: String): Boolean {
        val normalizedUrl = normalizeVaultUrl(vaultUrl)
        return credentialStore.containsKey(normalizedUrl)
    }
    
    /**
     * Returns a list of all registered vault URLs.
     * 
     * @return List of vault URLs with registered credentials
     */
    fun getRegisteredVaults(): List<String> {
        return credentialStore.keys.toList()
    }
    
    /**
     * Clears all registered credentials.
     * Useful for testing or reconfiguration.
     */
    fun clear() {
        logger.debug { "Clearing all registered credentials (${credentialStore.size} vaults)" }
        credentialStore.clear()
        tokenSupplierStore.clear()
    }
    
    /**
     * Returns the number of registered vaults.
     * 
     * @return Count of registered vaults
     */
    fun count(): Int = credentialStore.size
    
    /**
     * Normalizes a vault URL or key ID to just the vault base URL.
     * 
     * Examples:
     * - "https://my-vault.vault.azure.net" → "https://my-vault.vault.azure.net"
     * - "https://my-vault.vault.azure.net/keys/my-key" → "https://my-vault.vault.azure.net"
     * - "https://my-vault.vault.azure.net/keys/my-key/version" → "https://my-vault.vault.azure.net"
     * 
     * @param url The URL to normalize
     * @return Normalized base vault URL
     */
    internal fun normalizeVaultUrl(url: String): String {
        // Remove trailing slashes
        val trimmed = url.trimEnd('/')
        
        // If it's already just the base URL (no /keys/ path), return it
        if (!trimmed.contains("/keys/")) {
            return trimmed
        }
        
        // Extract base URL (everything before /keys/)
        val keysIndex = trimmed.indexOf("/keys/")
        return trimmed.substring(0, keysIndex)
    }
    
    /**
     * Extracts the vault URL from a full key ID.
     * 
     * @param keyId Full key ID (e.g., https://vault.azure.net/keys/key-name/version)
     * @return Vault base URL
     */
    fun extractVaultUrl(keyId: String): String {
        return normalizeVaultUrl(keyId)
    }
}

/**
 * Access token model used by token suppliers. Represents a bearer token and its expiration instant.
 */
data class AzureToken(
    val accessToken: String,
    val expiration: Instant
)

/**
 * Configuration class for bulk credential registration.
 * Can be used with configuration frameworks.
 */
@Serializable
data class AzureCredentialConfig(
    val vaults: List<VaultCredentials>
) {
    @Serializable
    data class VaultCredentials(
        val vaultUrl: String,
        val clientId: String,
        val clientSecret: String,
        val tenantId: String
    ) {
        fun toAzureAuth(): AzureAuth {
            return AzureAuth(
                clientId = clientId,
                clientSecret = clientSecret,
                tenantId = tenantId,
                keyVaultUrl = vaultUrl
            )
        }
    }
    
    /**
     * Registers all configured credentials with the provider.
     */
    fun registerAll() {
        vaults.forEach { vault ->
            AzureCredentialProvider.register(
                vaultUrl = vault.vaultUrl,
                auth = vault.toAzureAuth()
            )
        }
    }
}

