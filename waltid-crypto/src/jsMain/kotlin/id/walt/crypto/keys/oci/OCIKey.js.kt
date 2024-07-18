
    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getKeyId(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportJWK(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportJWKObject(): JsonObject {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>
    ): String {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getPublicKey(): Key {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    @JsPromise
    @JsExport.Ignore
    actual override suspend fun getMeta(): OciKeyMeta {
        TODO("Not yet implemented")
    }

    actual companion object {
        actual val DEFAULT_KEY_LENGTH: Int
            get() = TODO("Not yet implemented")

        @JsPromise
        @JsExport.Ignore
        actual suspend fun generateKey(config: OCIsdkMetadata): OCIKey {
            TODO("Not yet implemented")
        }


    }


}
