package id.walt.crypto2.jvm;

import java.util.concurrent.CompletionStage;

public interface JavaSigner {
    CompletionStage<byte[]> sign(byte[] data, JavaSignatureAlgorithm algorithm);
}
