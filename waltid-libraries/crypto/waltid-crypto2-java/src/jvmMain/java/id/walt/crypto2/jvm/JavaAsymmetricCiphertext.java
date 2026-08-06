package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Arrays;
import java.util.Objects;

public record JavaAsymmetricCiphertext(
        Type type,
        JavaAsymmetricEncryptionAlgorithm algorithm,
        byte[] data,
        @Nullable String provider,
        @Nullable String keyId,
        @Nullable String keyVersion,
        Map<String, String> context,
        byte[] providerData
) {
    public enum Type { RAW, OPAQUE }

    public JavaAsymmetricCiphertext {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(algorithm, "algorithm");
        data = Objects.requireNonNull(data, "data").clone();
        context = Map.copyOf(Objects.requireNonNull(context, "context"));
        providerData = Objects.requireNonNull(providerData, "providerData").clone();
        if (type == Type.RAW && (provider != null || keyId != null || keyVersion != null || !context.isEmpty() || providerData.length != 0)) {
            throw new IllegalArgumentException("Raw ciphertext cannot contain provider metadata");
        }
        if (type == Type.OPAQUE && (provider == null || keyId == null)) {
            throw new IllegalArgumentException("Opaque ciphertext requires provider and key ID");
        }
    }

    @Override public byte[] data() { return data.clone(); }
    @Override public byte[] providerData() { return providerData.clone(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavaAsymmetricCiphertext value)) return false;
        return type == value.type && algorithm.equals(value.algorithm) && Arrays.equals(data, value.data) &&
                Objects.equals(provider, value.provider) && Objects.equals(keyId, value.keyId) &&
                Objects.equals(keyVersion, value.keyVersion) && context.equals(value.context) &&
                Arrays.equals(providerData, value.providerData);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(type, algorithm, provider, keyId, keyVersion, context);
        result = 31 * result + Arrays.hashCode(data);
        return 31 * result + Arrays.hashCode(providerData);
    }

    public static JavaAsymmetricCiphertext raw(JavaAsymmetricEncryptionAlgorithm algorithm, byte[] data) {
        return new JavaAsymmetricCiphertext(Type.RAW, algorithm, data, null, null, null, Map.of(), new byte[0]);
    }
}
