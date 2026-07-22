package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

public interface JavaManagedKeyProvider {
    String id();

    CompletionStage<JavaManagedKey> generate(JavaGenerateManagedKeyRequest request);

    CompletionStage<JavaManagedKey> restore(JavaManagedKeyReference reference);

    default CompletionStage<@Nullable Void> close() {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
}
