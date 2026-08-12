package id.walt.crypto2.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Serializes storable keys as their versioned [StoredKey] record. */
object KeySerializer : KSerializer<Key> {
    override val descriptor: SerialDescriptor = StoredKey.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Key) {
        val storedKey = (value as? StorableKey)?.storedKey
            ?: throw SerializationException(
                "Cannot serialize ${value::class.simpleName ?: "anonymous Key implementation"}: " +
                    "it does not implement StorableKey. Only keys backed by a StoredKey descriptor can be " +
                    "serialized and later materialized with CryptoRuntime.restore.",
            )
        encoder.encodeSerializableValue(StoredKey.serializer(), storedKey)
    }

    override fun deserialize(decoder: Decoder): Key = decoder.decodeStoredKey().toHandle()
}

/** Serializes software keys directly as a versioned [StoredKey.Software] record. */
object SoftwareKeySerializer : KSerializer<SoftwareKey> {
    override val descriptor: SerialDescriptor = StoredKey.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SoftwareKey) {
        encoder.encodeSerializableValue(StoredKey.serializer(), value.storedKey)
    }

    override fun deserialize(decoder: Decoder): SoftwareKey = when (val storedKey = decoder.decodeStoredKey()) {
        is StoredKey.Software -> StorableSoftwareKeyHandle(storedKey)
        is StoredKey.Managed -> throw unexpectedKind("SoftwareKey", storedKey)
    }
}

/** Serializes managed keys directly as a versioned [StoredKey.Managed] record. */
object ManagedKeySerializer : KSerializer<ManagedKey> {
    override val descriptor: SerialDescriptor = StoredKey.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ManagedKey) {
        encoder.encodeSerializableValue(StoredKey.serializer(), value.storedKey)
    }

    override fun deserialize(decoder: Decoder): ManagedKey = when (val storedKey = decoder.decodeStoredKey()) {
        is StoredKey.Managed -> StorableManagedKeyHandle(storedKey)
        is StoredKey.Software -> throw unexpectedKind("ManagedKey", storedKey)
    }
}

internal class StorableSoftwareKeyHandle(
    override val storedKey: StoredKey.Software,
) : SoftwareKey {
    override val id: KeyId = storedKey.id
    override val spec: KeySpec = storedKey.spec
    override val usages: Set<KeyUsage> = storedKey.usages
    override val capabilities: KeyCapabilities = KeyCapabilities()

    override fun toString(): String =
        "SoftwareKeyHandle(id=${id.value}, restorationRequired=true; call CryptoRuntime.restore(storableKey))"
}

internal class StorableManagedKeyHandle(
    override val storedKey: StoredKey.Managed,
) : ManagedKey {
    override val id: KeyId = storedKey.id
    override val spec: KeySpec = storedKey.spec
    override val usages: Set<KeyUsage> = storedKey.usages
    override val capabilities: KeyCapabilities = KeyCapabilities()

    override fun toString(): String =
        "ManagedKeyHandle(id=${id.value}, restorationRequired=true; call CryptoRuntime.restore(storableKey))"
}

private fun Decoder.decodeStoredKey(): StoredKey = try {
    decodeSerializableValue(StoredKey.serializer())
} catch (cause: SerializationException) {
    throw cause
} catch (cause: IllegalArgumentException) {
    throw SerializationException(cause.message ?: "Invalid StoredKey", cause)
}

private fun StoredKey.toHandle(): Key = when (this) {
    is StoredKey.Software -> StorableSoftwareKeyHandle(this)
    is StoredKey.Managed -> StorableManagedKeyHandle(this)
}

private fun unexpectedKind(expected: String, actual: StoredKey): SerializationException = SerializationException(
    "Cannot decode ${actual::class.simpleName} StoredKey as $expected. " +
        "Decode it as Key and call CryptoRuntime.restore(storableKey) with the required provider.",
)
