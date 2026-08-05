package id.walt.crypto2.jvm;

import java.util.Objects;

public record JavaDigestAlgorithm(String name) {
    public JavaDigestAlgorithm {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Digest name cannot be blank");
    }
}
