package id.walt.crypto2.jvm;

import java.util.concurrent.CompletionStage;

public interface JavaKeyWrapper {
    CompletionStage<JavaWrappedKey> wrapKey(JavaEncodedKeyMaterial key, JavaNamedAlgorithm algorithm);
}
