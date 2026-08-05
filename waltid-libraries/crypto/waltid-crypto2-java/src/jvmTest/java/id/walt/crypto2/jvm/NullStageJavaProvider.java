package id.walt.crypto2.jvm;

import java.util.concurrent.CompletionStage;

public final class NullStageJavaProvider implements JavaManagedKeyProvider {
    @Override
    public String id() {
        return "null-stage";
    }

    @Override
    public CompletionStage<JavaManagedKey> generate(JavaGenerateManagedKeyRequest request) {
        return null;
    }

    @Override
    public CompletionStage<JavaManagedKey> restore(JavaManagedKeyReference reference) {
        return null;
    }
}
