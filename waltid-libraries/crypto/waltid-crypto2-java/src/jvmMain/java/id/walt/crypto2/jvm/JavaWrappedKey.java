package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Arrays;

public record JavaWrappedKey(
        Type type,
        JavaNamedAlgorithm algorithm,
        byte[] blob,
        JavaKeySpec wrappedKeySpec,
        @Nullable String wrappingKeyId,
        @Nullable String provider,
        @Nullable String keyVersion,
        byte[] providerData
) {
    public enum Type { RAW, OPAQUE }

    public JavaWrappedKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(algorithm, "algorithm");
        blob = Objects.requireNonNull(blob, "blob").clone();
        Objects.requireNonNull(wrappedKeySpec, "wrappedKeySpec");
        providerData = Objects.requireNonNull(providerData, "providerData").clone();
        if (type == Type.RAW && (provider != null || keyVersion != null || providerData.length != 0)) {
            throw new IllegalArgumentException("Raw wrapped key cannot contain provider metadata");
        }
        if (type == Type.OPAQUE && (provider == null || wrappingKeyId == null)) {
            throw new IllegalArgumentException("Opaque wrapped key requires provider and wrapping-key ID");
        }
    }

    @Override public byte[] blob() { return blob.clone(); }
    @Override public byte[] providerData() { return providerData.clone(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavaWrappedKey value)) return false;
        return type == value.type && algorithm.equals(value.algorithm) && Arrays.equals(blob, value.blob) &&
                wrappedKeySpec.equals(value.wrappedKeySpec) && Objects.equals(wrappingKeyId, value.wrappingKeyId) &&
                Objects.equals(provider, value.provider) && Objects.equals(keyVersion, value.keyVersion) &&
                Arrays.equals(providerData, value.providerData);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(type, algorithm, wrappedKeySpec, wrappingKeyId, provider, keyVersion);
        result = 31 * result + Arrays.hashCode(blob);
        return 31 * result + Arrays.hashCode(providerData);
    }
}
