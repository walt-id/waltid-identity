package id.walt.crypto2.jvm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ChangedIdentityJavaProvider implements JavaManagedKeyProvider {
    @Override
    public String id() {
        return "changed-identity";
    }

    @Override
    public CompletionStage<JavaManagedKey> generate(JavaGenerateManagedKeyRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<JavaManagedKey> restore(JavaManagedKeyReference reference) {
        var changed = new JavaManagedKeyReference(
                reference.getVersion(),
                reference.getId() + "-changed",
                reference.getSpec(),
                reference.getUsages(),
                reference.getProvider(),
                reference.getProviderSchemaVersion(),
                reference.getProviderData(),
                reference.getPublicKey(),
                reference.getMetadata()
        );
        return CompletableFuture.completedFuture(() -> changed);
    }
}
