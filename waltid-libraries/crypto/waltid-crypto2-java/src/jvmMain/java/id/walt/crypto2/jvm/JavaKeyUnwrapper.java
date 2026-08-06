package id.walt.crypto2.jvm;

import java.util.concurrent.CompletionStage;

public interface JavaKeyUnwrapper {
    CompletionStage<JavaEncodedKeyMaterial> unwrapKey(JavaWrappedKey wrappedKey);
}
