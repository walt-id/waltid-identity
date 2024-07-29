import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

@JsModule("jose")
@JsNonModule
external object jose {
    // -- Generate --

    interface GenerateKeyPairResult<T : KeyLike> {
        var privateKey: T
        var publicKey: T
    }

    interface GenerateKeyPairOptions {
        var crv: String?
            get() = definedExternally
            set(value) = definedExternally
        var modulusLength: Number?
            get() = definedExternally
            set(value) = definedExternally
        var extractable: Boolean?
            get() = definedExternally
            set(value) = definedExternally
    }

    fun <T : KeyLike> generateKeyPair(
        alg: String,
        options: GenerateKeyPairOptions = definedExternally
    ): Promise<GenerateKeyPairResult<T>>

    // -- Import --

    interface PEMImportOptions {
        var extractable: Boolean?
            get() = definedExternally
            set(value) = definedExternally
    }

    fun <T : KeyLike> importSPKI(spki: String, alg: String, options: PEMImportOptions = definedExternally): Promise<T>

    fun <T : KeyLike> importX509(x509: String, alg: String, options: PEMImportOptions = definedExternally): Promise<T>

    fun <T : KeyLike> importPKCS8(pkcs8: String, alg: String, options: PEMImportOptions = definedExternally): Promise<T>

    fun <T : KeyLike> importJWK(jwk: JWK, alg: String = definedExternally, octAsKeyObject: Boolean = definedExternally): Promise<T>

    // -- Export --
    fun exportSPKI(key: KeyLike): Promise<String>

    fun exportPKCS8(key: KeyLike): Promise<String>

    fun exportJWK(key: KeyLike): Promise<JWK>

    fun exportJWK(key: Uint8Array): Promise<JWK>

    // -- Sign --
    open class CompactSign(payload: Uint8Array) : ProduceJWT {
        open var _protectedHeader: Any
        open fun setProtectedHeader(protectedHeader: dynamic): CompactSign /* this */
        open fun sign(key: KeyLike, options: SignOptions = definedExternally): Promise<String>
    }

    // -- Verify --

    fun compactVerify(jws: String, key: KeyLike): Promise<CompactVerifyResult>

    // -- Thumbprint --

    fun calculateJwkThumbprint(jwk: JWK, digestAlgorithm: String /* "sha256" | "sha384" | "sha512" */ = definedExternally): Promise<String>

    fun calculateJwkThumbprintUri(
        jwk: JWK,
        digestAlgorithm: String /* "sha256" | "sha384" | "sha512" */ = definedExternally
    ): Promise<String>
}
