package id.walt.crypto2.jvm;

import id.walt.crypto2.keys.EncodedKey;

import java.util.concurrent.CompletionStage;

public interface JavaKeyAgreement {
    CompletionStage<byte[]> generateSharedSecret(EncodedKey peerPublicKey, JavaNamedAlgorithm algorithm);
}
