package id.walt.crypto2.jvm;

import id.walt.crypto2.providers.ManagedKeyProvider;
import id.walt.crypto2.keys.KeyDeletionResult;
import id.walt.crypto2.keys.KeyUsage;
import id.walt.crypto2.keys.EncodedKey;
import id.walt.crypto2.serialization.BinaryData;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.nio.charset.StandardCharsets;

public final class TestJavaManagedKeyProvider implements JavaManagedKeyProvider {
    private boolean closed;

    public static List<ManagedKeyProvider> discoverProviders() {
        return JavaManagedKeyProviders.load();
    }

    public boolean isClosed() {
        return closed;
    }

    public static boolean invalidDtosAreRejected() {
        try {
            new JavaSignatureAlgorithm(
                    JavaSignatureAlgorithm.Type.ECDSA,
                    null,
                    null,
                    null,
                    null,
                    null,
                    java.util.Map.of()
            );
            return false;
        } catch (NullPointerException | IllegalArgumentException expected) {
            return true;
        }
    }

    public static boolean ciphertextValueEquality() {
        var algorithm = JavaAsymmetricEncryptionAlgorithm.rsaOaep(
                new JavaDigestAlgorithm("SHA-256"),
                new JavaDigestAlgorithm("SHA-256")
        );
        return JavaAsymmetricCiphertext.raw(algorithm, new byte[]{1, 2})
                .equals(JavaAsymmetricCiphertext.raw(algorithm, new byte[]{1, 2}));
    }

    @Override
    public String id() {
        return "java-test";
    }

    @Override
    public CompletionStage<JavaManagedKey> generate(JavaGenerateManagedKeyRequest request) {
        var reference = new JavaManagedKeyReference(
                1,
                request.getId(),
                request.getSpec(),
                request.getUsages(),
                id(),
                1,
                request.getProviderOptions(),
                null,
                request.getMetadata()
        );
        return CompletableFuture.completedFuture(new TestJavaManagedKey(reference));
    }

    @Override
    public CompletionStage<JavaManagedKey> restore(JavaManagedKeyReference reference) {
        return CompletableFuture.completedFuture(new TestJavaManagedKey(reference));
    }

    @Override
    public CompletionStage<Void> close() {
        closed = true;
        return CompletableFuture.completedFuture(null);
    }

    private record TestJavaManagedKey(JavaManagedKeyReference reference) implements JavaManagedKey {
        @Override
        public JavaManagedKeyReference getReference() {
            return reference;
        }

        @Override
        public java.util.Set<JavaSignatureAlgorithm> signatureAlgorithms() {
            if (!reference.getUsages().contains(KeyUsage.SIGN) && !reference.getUsages().contains(KeyUsage.VERIFY)) {
                return java.util.Set.of();
            }
            return java.util.Set.of(JavaSignatureAlgorithm.ecdsa(
                    new JavaDigestAlgorithm("SHA-256"),
                    "IEEE_P1363"
            ));
        }

        @Override
        public boolean supportsSignatureAlgorithm(JavaSignatureAlgorithm algorithm) {
            return signatureAlgorithms().contains(algorithm) ||
                    algorithm.type() == JavaSignatureAlgorithm.Type.CUSTOM &&
                            "com.example.custom".equals(algorithm.customId());
        }

        @Override
        public java.util.Set<JavaNamedAlgorithm> keyAgreementAlgorithms() {
            if (!reference.getUsages().contains(KeyUsage.KEY_AGREEMENT)) return java.util.Set.of();
            return java.util.Set.of(JavaNamedAlgorithm.named("ECDH", java.util.Map.of("profile", "custom")));
        }

        @Override
        public java.util.Set<JavaNamedAlgorithm> keyWrappingAlgorithms() {
            if (!reference.getUsages().contains(KeyUsage.WRAP) && !reference.getUsages().contains(KeyUsage.UNWRAP)) {
                return java.util.Set.of();
            }
            return java.util.Set.of(JavaNamedAlgorithm.builtin("A256KW"));
        }

        @Override
        public JavaKeyAgreement keyAgreement() {
            if (!reference.getUsages().contains(KeyUsage.KEY_AGREEMENT)) return null;
            return (peerPublicKey, algorithm) -> CompletableFuture.completedFuture(new byte[]{1, 2, 3});
        }

        @Override
        public JavaKeyWrapper keyWrapper() {
            if (!reference.getUsages().contains(KeyUsage.WRAP)) return null;
            return (key, algorithm) -> CompletableFuture.completedFuture(new JavaWrappedKey(
                    JavaWrappedKey.Type.RAW,
                    algorithm,
                    new byte[]{1, 2},
                    JavaKeySpec.symmetric("AES", 256),
                    reference.getId(),
                    null,
                    null,
                    new byte[0]
            ));
        }

        @Override
        public JavaKeyUnwrapper keyUnwrapper() {
            if (!reference.getUsages().contains(KeyUsage.UNWRAP)) return null;
            return wrappedKey -> CompletableFuture.completedFuture(
                    new JavaEncodedKeyMaterial(
                            wrappedKey.wrappedKeySpec(),
                            new EncodedKey.Jwk(
                                    new BinaryData(
                                            ("{\"kty\":\"oct\",\"k\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}")
                                                    .getBytes(StandardCharsets.UTF_8)
                                    ),
                                    true
                            )
                    )
            );
        }

        @Override
        public JavaSigner signer() {
            if (!reference.getUsages().contains(KeyUsage.SIGN)) return null;
            return (data, algorithm) -> CompletableFuture.supplyAsync(() -> {
                if (algorithm.type() != JavaSignatureAlgorithm.Type.ECDSA ||
                        !algorithm.digest().name().equals("SHA-256") ||
                        !algorithm.encoding().equals("IEEE_P1363")) {
                    throw new IllegalArgumentException("Unexpected signature algorithm");
                }
                if (data.length == 0) {
                    throw new IllegalStateException("Signing failed");
                }
                return data.clone();
            });
        }

        @Override
        public JavaVerifier verifier() {
            if (!reference.getUsages().contains(KeyUsage.VERIFY)) return null;
            return (data, signature, algorithm) ->
                    CompletableFuture.supplyAsync(() -> Arrays.equals(data, signature));
        }

        @Override
        public java.util.Set<JavaAsymmetricEncryptionAlgorithm> encryptionAlgorithms() {
            if (!reference.getUsages().contains(KeyUsage.ENCRYPT) && !reference.getUsages().contains(KeyUsage.DECRYPT)) {
                return java.util.Set.of();
            }
            var digest = new JavaDigestAlgorithm("SHA-256");
            return java.util.Set.of(JavaAsymmetricEncryptionAlgorithm.rsaOaep(digest, digest));
        }

        @Override
        public JavaEncryptor encryptor() {
            if (!reference.getUsages().contains(KeyUsage.ENCRYPT)) return null;
            return (plaintext, algorithm, associatedData) ->
                    CompletableFuture.completedFuture(JavaAsymmetricCiphertext.raw(algorithm, plaintext));
        }

        @Override
        public JavaDecryptor decryptor() {
            if (!reference.getUsages().contains(KeyUsage.DECRYPT)) return null;
            return (ciphertext, associatedData) -> CompletableFuture.completedFuture(ciphertext.data());
        }

        @Override
        public JavaKeyDeleter deleter() {
            return () -> CompletableFuture.completedFuture(KeyDeletionResult.Deleted.INSTANCE);
        }
    }
}
