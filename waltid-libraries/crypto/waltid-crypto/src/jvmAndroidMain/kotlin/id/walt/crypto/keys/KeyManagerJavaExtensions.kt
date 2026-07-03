package id.walt.crypto.keys

import kotlin.jvm.internal.Reflection

object KeyManagerJavaExtensions {

    /**
     * Use like this from Java:
     * ```
     * KeyManagerJavaExtensions.INSTANCE.register(MyJavaBasedKey.class, "theTypeId",
     *     keyGenerationRequest -> {
     *         String backend = keyGenerationRequest.getBackend();
     *         KeyType keyType = keyGenerationRequest.getKeyType();
     *
     *         // Return key here
     *         return null;
     *     }
     * );
     * ```
     */
    fun register(javaClass: Class<out Key>, typeId: String, createFunction: (KeyGenerationRequest) -> Key) =
        KeyManager.registerByType(
            type = Reflection.typeOf(javaClass),
            typeId = typeId,
            createFunction = createFunction
        )

}
