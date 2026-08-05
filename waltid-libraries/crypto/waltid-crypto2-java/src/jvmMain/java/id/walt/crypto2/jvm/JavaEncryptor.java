package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

public interface JavaEncryptor {
    CompletionStage<JavaAsymmetricCiphertext> encrypt(
            byte[] plaintext,
            JavaAsymmetricEncryptionAlgorithm algorithm,
            byte @Nullable [] associatedData
    );
}
