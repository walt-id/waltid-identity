package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record JavaKeySpec(
        Type type,
        @Nullable String name,
        @Nullable Integer bits,
        Map<String, String> parameters
) {
    public enum Type { EC, EDWARDS, MONTGOMERY, RSA, SYMMETRIC, CUSTOM }

    public JavaKeySpec {
        Objects.requireNonNull(type, "type");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        switch (type) {
            case EC, EDWARDS, MONTGOMERY -> {
                Objects.requireNonNull(name, "curve name");
                if (bits != null || !parameters.isEmpty()) throw new IllegalArgumentException("Invalid curve key spec");
            }
            case RSA -> {
                if (name != null || bits == null || bits <= 0 || !parameters.isEmpty()) {
                    throw new IllegalArgumentException("Invalid RSA key spec");
                }
            }
            case SYMMETRIC -> {
                Objects.requireNonNull(name, "symmetric key family");
                if (bits == null || bits <= 0 || !parameters.isEmpty()) {
                    throw new IllegalArgumentException("Invalid symmetric key spec");
                }
            }
            case CUSTOM -> {
                Objects.requireNonNull(name, "custom key family");
                if (bits != null) throw new IllegalArgumentException("Invalid custom key spec");
            }
        }
    }

    public static JavaKeySpec ec(String curve) { return new JavaKeySpec(Type.EC, curve, null, Map.of()); }
    public static JavaKeySpec edwards(String curve) { return new JavaKeySpec(Type.EDWARDS, curve, null, Map.of()); }
    public static JavaKeySpec montgomery(String curve) { return new JavaKeySpec(Type.MONTGOMERY, curve, null, Map.of()); }
    public static JavaKeySpec rsa(int bits) { return new JavaKeySpec(Type.RSA, null, bits, Map.of()); }
    public static JavaKeySpec symmetric(String family, int bits) {
        return new JavaKeySpec(Type.SYMMETRIC, family, bits, Map.of());
    }
    public static JavaKeySpec custom(String family, Map<String, String> parameters) {
        return new JavaKeySpec(Type.CUSTOM, family, null, parameters);
    }
}
