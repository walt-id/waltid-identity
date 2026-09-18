package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

/**
 * A Java-facing handle to a generated or restored software key, returned by
 * {@link JavaSoftwareKeys#generate}.
 */
public interface JavaSoftwareKey {
    String id();

    /** The stored key, serialized to JSON (the same format used by the Kotlin API). */
    String storedKeyJson();

    @Nullable JavaSigner signer();

    @Nullable JavaVerifier verifier();
}
