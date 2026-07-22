package id.walt.crypto2.jvm;

import java.util.concurrent.CompletionStage;

public interface JavaVerifier {
    CompletionStage<Boolean> verify(byte[] data, byte[] signature, JavaSignatureAlgorithm algorithm);
}
