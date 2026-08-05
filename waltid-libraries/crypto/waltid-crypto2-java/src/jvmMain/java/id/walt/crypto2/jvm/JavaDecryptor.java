package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

public interface JavaDecryptor {
    CompletionStage<byte[]> decrypt(
            JavaAsymmetricCiphertext ciphertext,
            byte @Nullable [] associatedData
    );
}
