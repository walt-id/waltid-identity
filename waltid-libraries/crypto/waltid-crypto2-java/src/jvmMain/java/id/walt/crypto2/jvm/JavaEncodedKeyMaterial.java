package id.walt.crypto2.jvm;

import id.walt.crypto2.keys.EncodedKey;

import java.util.Objects;

public record JavaEncodedKeyMaterial(JavaKeySpec spec, EncodedKey key) {
    public JavaEncodedKeyMaterial {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(key, "key");
    }
}
