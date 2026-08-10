package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record JavaAsymmetricEncryptionAlgorithm(
        Type type,
        @Nullable JavaDigestAlgorithm digest,
        @Nullable JavaDigestAlgorithm mgfDigest,
        @Nullable String customId,
        Map<String, String> parameters
) {
    public enum Type { RSA_OAEP, RSA_PKCS1, CUSTOM }

    public JavaAsymmetricEncryptionAlgorithm {
        Objects.requireNonNull(type, "type");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        switch (type) {
            case RSA_OAEP -> {
                Objects.requireNonNull(digest, "digest");
                Objects.requireNonNull(mgfDigest, "MGF digest");
                if (customId != null || !parameters.isEmpty()) throw new IllegalArgumentException("Invalid RSA OAEP parameters");
            }
            case RSA_PKCS1 -> {
                if (digest != null || mgfDigest != null || customId != null || !parameters.isEmpty()) {
                    throw new IllegalArgumentException("Invalid RSA PKCS1 parameters");
                }
            }
            case CUSTOM -> {
                Objects.requireNonNull(customId, "custom algorithm ID");
                if (digest != null || mgfDigest != null) throw new IllegalArgumentException("Invalid custom encryption parameters");
            }
        }
    }

    public static JavaAsymmetricEncryptionAlgorithm rsaOaep(
            JavaDigestAlgorithm digest,
            JavaDigestAlgorithm mgfDigest
    ) {
        return new JavaAsymmetricEncryptionAlgorithm(Type.RSA_OAEP, digest, mgfDigest, null, Map.of());
    }

    public static JavaAsymmetricEncryptionAlgorithm rsaPkcs1() {
        return new JavaAsymmetricEncryptionAlgorithm(Type.RSA_PKCS1, null, null, null, Map.of());
    }

    public static JavaAsymmetricEncryptionAlgorithm custom(String id, Map<String, String> parameters) {
        return new JavaAsymmetricEncryptionAlgorithm(Type.CUSTOM, null, null, id, parameters);
    }
}
