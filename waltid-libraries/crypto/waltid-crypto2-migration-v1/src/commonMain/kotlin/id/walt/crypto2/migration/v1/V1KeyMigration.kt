package id.walt.crypto2.migration.v1

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.inferKeySpec
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class V1KeyMigration(
    managedMigrators: Map<String, V1ManagedKeyMigrator> = emptyMap(),
    private val platformMigrator: V1PlatformKeyMigrator? = null,
) {
    private val managedMigrators = managedMigrators.toMap()

    suspend fun migrate(
        recordId: KeyId,
        serialized: String,
        usages: Set<KeyUsage>,
    ): StoredKey = migrate(recordId, parseObject(serialized), usages)

    suspend fun migrate(
        recordId: KeyId,
        serialized: JsonObject,
        usages: Set<KeyUsage>,
    ): StoredKey {
        require(usages.isNotEmpty()) { "Migration requires explicit key usages" }
        return when (val type = serialized.requiredString("type")) {
            "jwk" -> migrateJwk(recordId, serialized.requiredObject("jwk"), usages)
            else -> migrateManaged(recordId, type, serialized, usages)
        }
    }

    suspend fun migrateMobileReference(record: V1MobileKeyReference): StoredKey {
        require(record.usages.isNotEmpty()) { "Migration requires explicit key usages" }
        return if (record.platformBacked) {
            val migrator = platformMigrator ?: throw V1KeyMigrationException.MissingPlatformMigrator(record.platform)
            migrator.migrate(record).also { migrated ->
                require(migrated.id == record.id) { "Platform migration changed the key ID" }
                require(migrated.spec == record.keyType.toKeySpec()) { "Platform migration changed the key specification" }
                require(migrated.usages == record.usages) { "Platform migration changed key usages" }
            }
        } else {
            val material = record.keyMaterial ?: throw V1KeyMigrationException.MissingKeyMaterial(record.id)
            migrateJwk(record.id, parseObject(material), record.usages)
        }
    }

    private fun migrateJwk(recordId: KeyId, jwk: JsonObject, usages: Set<KeyUsage>): StoredKey.Software {
        val privateMaterial = jwk.containsPrivateMaterial()
        return EncodedKey.Jwk(
            data = BinaryData(json.encodeToString(jwk).encodeToByteArray()),
            privateMaterial = privateMaterial,
        ).toStoredSoftwareKey(
            id = recordId,
            usages = usages,
        )
    }

    private suspend fun migrateManaged(
        recordId: KeyId,
        type: String,
        source: JsonObject,
        usages: Set<KeyUsage>,
    ): StoredKey.Managed {
        val migrator = managedMigrators[type] ?: throw V1KeyMigrationException.MissingManagedMigrator(type)
        val keyType = source["_keyType"]?.jsonPrimitive?.content
        val publicJwk = source.cachedPublicJwk()
        val record = V1ManagedKeyRecord(
            id = recordId,
            type = type,
            source = source,
            spec = publicJwk?.toKeySpec() ?: keyType?.toKeySpec(),
            publicJwk = publicJwk,
            usages = usages,
        )
        return migrator.migrate(record).also { migrated ->
            require(migrated.id == recordId) { "Managed migration changed the key ID" }
            require(migrated.usages == usages) { "Managed migration changed key usages" }
            record.spec?.let { require(migrated.spec == it) { "Managed migration changed the key specification" } }
            require(migrated.providerData.containsNoneOf(source.legacySecrets(type))) {
                "Managed migration persisted legacy credentials"
            }
            require(migrated.metadata.values.none { value -> source.legacySecrets(type).any(value::contains) }) {
                "Managed migration metadata contains legacy credentials"
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }
    }
}

data class V1ManagedKeyRecord(
    val id: KeyId,
    val type: String,
    val source: JsonObject,
    val spec: KeySpec?,
    val publicJwk: JsonObject?,
    val usages: Set<KeyUsage>,
)

fun interface V1ManagedKeyMigrator {
    suspend fun migrate(record: V1ManagedKeyRecord): StoredKey.Managed
}

enum class V1MobilePlatform {
    ANDROID,
    IOS,
}

data class V1MobileKeyReference(
    val id: KeyId,
    val keyType: String,
    val platform: V1MobilePlatform,
    val platformBacked: Boolean,
    val keyMaterial: String?,
    val usages: Set<KeyUsage>,
)

fun interface V1PlatformKeyMigrator {
    suspend fun migrate(record: V1MobileKeyReference): StoredKey.Managed
}

sealed class V1KeyMigrationException(message: String) : IllegalArgumentException(message) {
    class MissingManagedMigrator(type: String) : V1KeyMigrationException("No v1 migrator is registered for: $type")
    class MissingPlatformMigrator(platform: V1MobilePlatform) :
        V1KeyMigrationException("No v1 platform migrator is registered for: $platform")
    class MissingKeyMaterial(id: KeyId) : V1KeyMigrationException("Mobile software key has no material: ${id.value}")
}

private fun parseObject(value: String): JsonObject =
    Json.parseToJsonElement(value).let { it as? JsonObject ?: throw IllegalArgumentException("V1 key must be a JSON object") }

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name]?.jsonObject ?: throw IllegalArgumentException("V1 key is missing object: $name")

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("V1 key is missing string: $name")

private fun JsonObject.containsPrivateMaterial(): Boolean = when (requiredString("kty")) {
    "oct" -> "k" in this
    else -> "d" in this
}

private fun JsonObject.toKeySpec(): KeySpec = EncodedKey.Jwk(
    data = BinaryData(Json.encodeToString(this).encodeToByteArray()),
    privateMaterial = containsPrivateMaterial(),
).inferKeySpec()

private fun String.toKeySpec(): KeySpec = when (this) {
    "Ed25519" -> KeySpec.Edwards(EdwardsCurve.ED25519)
    "secp256k1" -> KeySpec.Ec(EcCurve.SECP256K1)
    "secp256r1" -> KeySpec.Ec(EcCurve.P256)
    "secp384r1" -> KeySpec.Ec(EcCurve.P384)
    "secp521r1" -> KeySpec.Ec(EcCurve.P521)
    "RSA" -> KeySpec.Rsa(2048)
    "RSA3072" -> KeySpec.Rsa(3072)
    "RSA4096" -> KeySpec.Rsa(4096)
    else -> throw IllegalArgumentException("Unsupported v1 key type: $this")
}

private fun JsonObject.legacySecrets(type: String): Set<String> = when (type) {
    "tse" -> valuesAt(
        "accessKey",
        "auth.accessKey",
        "auth.roleId",
        "auth.secretId",
        "auth.userpassPath",
        "auth.username",
        "auth.password",
    )
    "aws-rest-api" -> valuesAt(
        "config.auth.accessKeyId",
        "config.auth.secretAccessKey",
        "config.auth.roleName",
    )
    "azure-rest-api" -> valuesAt("auth.clientId", "auth.clientSecret")
    "oci-rest-api" -> valuesAt(
        "config.tenancyOcid",
        "config.userOcid",
        "config.fingerprint",
        "config.signingKeyPem",
    )
    else -> emptySet()
}

private fun JsonObject.valuesAt(vararg paths: String): Set<String> = paths.mapNotNull { path ->
    var current: JsonElement = this
    path.split('.').forEach { name ->
        current = (current as? JsonObject)?.get(name) ?: return@mapNotNull null
    }
    current.jsonPrimitive.content.takeIf(String::isNotBlank)
}.toSet()

private fun JsonObject.cachedPublicJwk(): JsonObject? {
    val cached = this["_publicKey"] ?: return null
    return when (cached) {
        is JsonObject -> if (cached["type"]?.jsonPrimitive?.content == "jwk") {
            cached["jwk"]?.jsonObject
        } else cached.takeIf { "kty" in it }
        else -> cached.jsonPrimitive.content.takeIf(String::isNotBlank)?.let(::parseObject)
    }
}

private fun BinaryData.containsNoneOf(secrets: Set<String>): Boolean {
    val providerData = toByteArray().decodeToString()
    return secrets.none(providerData::contains)
}
