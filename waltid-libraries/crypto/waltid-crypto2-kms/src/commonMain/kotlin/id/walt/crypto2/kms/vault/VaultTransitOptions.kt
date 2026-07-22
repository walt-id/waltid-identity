package id.walt.crypto2.kms.vault

import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.requireHttpEndpoint
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class VaultTransitOptions(
    val apiBaseUrl: String,
    val credentialReference: CredentialReference,
    val transitMount: String = "transit",
    val keyName: String? = null,
    val namespace: String? = null,
) {
    init {
        requireHttpEndpoint(apiBaseUrl, "Vault API base URL")
        require(transitMount.isNotBlank() && '/' !in transitMount) { "Vault Transit mount must be one path segment" }
        require(keyName == null || keyName.isNotBlank()) { "Vault key name cannot be blank" }
        require(namespace == null || namespace.isNotBlank()) { "Vault namespace cannot be blank" }
    }

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

        internal fun decode(data: BinaryData): VaultTransitOptions =
            json.decodeFromString(data.toByteArray().decodeToString())
    }
}

sealed interface VaultCredential {
    class Token(val value: String) : VaultCredential {
        init {
            require(value.isNotBlank()) { "Vault token cannot be blank" }
        }
    }

    class AppRole(
        val roleId: String,
        val secretId: String,
        val mount: String = "approle",
    ) : VaultCredential {
        init {
            require(roleId.isNotBlank()) { "Vault AppRole role ID cannot be blank" }
            require(secretId.isNotBlank()) { "Vault AppRole secret ID cannot be blank" }
            require(mount.isNotBlank() && '/' !in mount) { "Vault AppRole mount must be one path segment" }
        }
    }

    class UserPassword(
        val username: String,
        val password: String,
        val mount: String = "userpass",
    ) : VaultCredential {
        init {
            require(username.isNotBlank()) { "Vault username cannot be blank" }
            require(password.isNotBlank()) { "Vault password cannot be blank" }
            require(mount.isNotBlank() && '/' !in mount) { "Vault userpass mount must be one path segment" }
        }
    }
}

fun interface VaultCredentialResolver {
    suspend fun resolve(reference: CredentialReference): VaultCredential
}
