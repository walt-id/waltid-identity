package id.walt.crypto2.jvm;

import id.walt.crypto2.keys.KeyEncodingFormat;
import id.walt.crypto2.keys.KeyUsage;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record JavaGenerateSoftwareKeyRequest(
        String id,
        JavaKeySpec spec,
        Set<KeyUsage> usages,
        KeyEncodingFormat keyEncoding,
        Map<String, String> metadata
) {
    public JavaGenerateSoftwareKeyRequest {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        Objects.requireNonNull(spec, "spec");
        usages = Set.copyOf(Objects.requireNonNull(usages, "usages"));
        Objects.requireNonNull(keyEncoding, "keyEncoding");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    public static JavaGenerateSoftwareKeyRequest of(String id, JavaKeySpec spec, Set<KeyUsage> usages) {
        return new JavaGenerateSoftwareKeyRequest(id, spec, usages, KeyEncodingFormat.JWK, Map.of());
    }
}
