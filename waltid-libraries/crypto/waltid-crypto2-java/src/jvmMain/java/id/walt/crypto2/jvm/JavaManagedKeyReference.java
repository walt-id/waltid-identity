package id.walt.crypto2.jvm;

import id.walt.crypto2.keys.EncodedKey;
import id.walt.crypto2.keys.KeyUsage;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JavaManagedKeyReference {
    private final int version;
    private final String id;
    private final JavaKeySpec spec;
    private final Set<KeyUsage> usages;
    private final String provider;
    private final int providerSchemaVersion;
    private final byte[] providerData;
    private final @Nullable EncodedKey publicKey;
    private final Map<String, String> metadata;

    public JavaManagedKeyReference(
            int version,
            String id,
            JavaKeySpec spec,
            Set<KeyUsage> usages,
            String provider,
            int providerSchemaVersion,
            byte[] providerData,
            @Nullable EncodedKey publicKey,
            Map<String, String> metadata
    ) {
        this.version = version;
        this.id = Objects.requireNonNull(id, "id");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.usages = Set.copyOf(Objects.requireNonNull(usages, "usages"));
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerSchemaVersion = providerSchemaVersion;
        this.providerData = Objects.requireNonNull(providerData, "providerData").clone();
        this.publicKey = publicKey;
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    public int getVersion() { return version; }
    public String getId() { return id; }
    public JavaKeySpec getSpec() { return spec; }
    public Set<KeyUsage> getUsages() { return usages; }
    public String getProvider() { return provider; }
    public int getProviderSchemaVersion() { return providerSchemaVersion; }
    public byte[] getProviderData() { return providerData.clone(); }
    public @Nullable EncodedKey getPublicKey() { return publicKey; }
    public Map<String, String> getMetadata() { return metadata; }
}
