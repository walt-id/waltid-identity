package id.walt.crypto2.jvm;

import id.walt.crypto2.keys.KeyUsage;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JavaGenerateManagedKeyRequest {
    private final String id;
    private final JavaKeySpec spec;
    private final Set<KeyUsage> usages;
    private final byte[] providerOptions;
    private final Map<String, String> metadata;

    public JavaGenerateManagedKeyRequest(
            String id,
            JavaKeySpec spec,
            Set<KeyUsage> usages,
            byte[] providerOptions,
            Map<String, String> metadata
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.usages = Set.copyOf(Objects.requireNonNull(usages, "usages"));
        this.providerOptions = Objects.requireNonNull(providerOptions, "providerOptions").clone();
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    public String getId() { return id; }
    public JavaKeySpec getSpec() { return spec; }
    public Set<KeyUsage> getUsages() { return usages; }
    public byte[] getProviderOptions() { return providerOptions.clone(); }
    public Map<String, String> getMetadata() { return metadata; }
}
