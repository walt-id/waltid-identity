package id.walt.crypto2.jvm;

import org.jspecify.annotations.Nullable;

import java.util.Set;

public interface JavaManagedKey {
    JavaManagedKeyReference getReference();

    default Set<JavaSignatureAlgorithm> signatureAlgorithms() { return Set.of(); }
    default Set<JavaAsymmetricEncryptionAlgorithm> encryptionAlgorithms() { return Set.of(); }
    default Set<JavaNamedAlgorithm> keyAgreementAlgorithms() { return Set.of(); }
    default Set<JavaNamedAlgorithm> keyWrappingAlgorithms() { return Set.of(); }
    default boolean supportsSignatureAlgorithm(JavaSignatureAlgorithm algorithm) {
        return signatureAlgorithms().contains(algorithm);
    }
    default boolean supportsEncryptionAlgorithm(JavaAsymmetricEncryptionAlgorithm algorithm) {
        return encryptionAlgorithms().contains(algorithm);
    }
    default boolean supportsKeyAgreementAlgorithm(JavaNamedAlgorithm algorithm) {
        return keyAgreementAlgorithms().contains(algorithm);
    }
    default boolean supportsKeyWrappingAlgorithm(JavaNamedAlgorithm algorithm) {
        return keyWrappingAlgorithms().contains(algorithm);
    }

    default @Nullable JavaSigner signer() {
        return null;
    }

    default @Nullable JavaVerifier verifier() {
        return null;
    }

    default @Nullable JavaEncryptor encryptor() {
        return null;
    }

    default @Nullable JavaDecryptor decryptor() {
        return null;
    }

    default @Nullable JavaKeyAgreement keyAgreement() {
        return null;
    }

    default @Nullable JavaKeyWrapper keyWrapper() {
        return null;
    }

    default @Nullable JavaKeyUnwrapper keyUnwrapper() {
        return null;
    }

    default @Nullable JavaKeyDeleter deleter() {
        return null;
    }
}
