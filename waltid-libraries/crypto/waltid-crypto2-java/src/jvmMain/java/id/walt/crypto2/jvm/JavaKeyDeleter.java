package id.walt.crypto2.jvm;

import id.walt.crypto2.keys.KeyDeletionResult;

import java.util.concurrent.CompletionStage;

public interface JavaKeyDeleter {
    CompletionStage<KeyDeletionResult> delete();
}
