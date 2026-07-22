package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record JavaSignatureAlgorithm(
        Type type,
        @Nullable JavaDigestAlgorithm digest,
        @Nullable String encoding,
        @Nullable JavaDigestAlgorithm mgfDigest,
        @Nullable Integer saltLengthBytes,
        @Nullable String customId,
        Map<String, String> parameters
) {
    public enum Type { ECDSA, EDDSA, RSA_PKCS1, RSA_PSS, CUSTOM }

    public JavaSignatureAlgorithm {
        Objects.requireNonNull(type, "type");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        switch (type) {
            case ECDSA -> {
                Objects.requireNonNull(digest, "digest");
                Objects.requireNonNull(encoding, "encoding");
                if (mgfDigest != null || saltLengthBytes != null || customId != null || !parameters.isEmpty()) {
                    throw new IllegalArgumentException("Invalid ECDSA parameters");
                }
            }
            case EDDSA -> {
                if (digest != null || encoding != null || mgfDigest != null || saltLengthBytes != null ||
                        customId != null || !parameters.isEmpty()) throw new IllegalArgumentException("Invalid EdDSA parameters");
            }
            case RSA_PKCS1 -> {
                Objects.requireNonNull(digest, "digest");
                if (encoding != null || mgfDigest != null || saltLengthBytes != null || customId != null ||
                        !parameters.isEmpty()) throw new IllegalArgumentException("Invalid RSA PKCS1 parameters");
            }
            case RSA_PSS -> {
                Objects.requireNonNull(digest, "digest");
                Objects.requireNonNull(mgfDigest, "MGF digest");
                if (encoding != null || customId != null || !parameters.isEmpty() ||
                        (saltLengthBytes != null && saltLengthBytes < 0)) {
                    throw new IllegalArgumentException("Invalid RSA PSS parameters");
                }
            }
            case CUSTOM -> {
                Objects.requireNonNull(customId, "custom algorithm ID");
                if (digest != null || encoding != null || mgfDigest != null || saltLengthBytes != null) {
                    throw new IllegalArgumentException("Invalid custom signature parameters");
                }
            }
        }
    }

    public static JavaSignatureAlgorithm ecdsa(JavaDigestAlgorithm digest, String encoding) {
        return new JavaSignatureAlgorithm(Type.ECDSA, digest, encoding, null, null, null, Map.of());
    }

    public static JavaSignatureAlgorithm edDsa() {
        return new JavaSignatureAlgorithm(Type.EDDSA, null, null, null, null, null, Map.of());
    }

    public static JavaSignatureAlgorithm rsaPkcs1(JavaDigestAlgorithm digest) {
        return new JavaSignatureAlgorithm(Type.RSA_PKCS1, digest, null, null, null, null, Map.of());
    }

    public static JavaSignatureAlgorithm rsaPss(
            JavaDigestAlgorithm digest,
            JavaDigestAlgorithm mgfDigest,
            @Nullable Integer saltLengthBytes
    ) {
        return new JavaSignatureAlgorithm(Type.RSA_PSS, digest, null, mgfDigest, saltLengthBytes, null, Map.of());
    }

    public static JavaSignatureAlgorithm custom(String id, Map<String, String> parameters) {
        return new JavaSignatureAlgorithm(Type.CUSTOM, null, null, null, null, id, parameters);
    }
}
