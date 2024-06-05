@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

import org.khronos.webgl.Uint8Array
import tsstdlib.Omit
import tsstdlib.Pick
import kotlin.js.Date
import kotlin.js.Promise

external interface KeyLike {
    var type: String
}

external interface `T$0` {
    var d: String?
        get() = definedExternally
        set(value) = definedExternally
    var r: String?
        get() = definedExternally
        set(value) = definedExternally
    var t: String?
        get() = definedExternally
        set(value) = definedExternally
}

@Suppress("DEPRECATION")
external interface JWK {
    var alg: String?
        get() = definedExternally
        set(value) = definedExternally
    var crv: String?
        get() = definedExternally
        set(value) = definedExternally
    var d: String?
        get() = definedExternally
        set(value) = definedExternally
    var dp: String?
        get() = definedExternally
        set(value) = definedExternally
    var dq: String?
        get() = definedExternally
        set(value) = definedExternally
    var e: String?
        get() = definedExternally
        set(value) = definedExternally
    var ext: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var k: String?
        get() = definedExternally
        set(value) = definedExternally
    var key_ops: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var kid: String?
        get() = definedExternally
        set(value) = definedExternally
    var kty: String?
        get() = definedExternally
        set(value) = definedExternally
    var n: String?
        get() = definedExternally
        set(value) = definedExternally
    var oth: Array<`T$0`>?
        get() = definedExternally
        set(value) = definedExternally
    var p: String?
        get() = definedExternally
        set(value) = definedExternally
    var q: String?
        get() = definedExternally
        set(value) = definedExternally
    var qi: String?
        get() = definedExternally
        set(value) = definedExternally
    var use: String?
        get() = definedExternally
        set(value) = definedExternally
    var x: String?
        get() = definedExternally
        set(value) = definedExternally
    var y: String?
        get() = definedExternally
        set(value) = definedExternally
    var x5c: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var x5t: String?
        get() = definedExternally
        set(value) = definedExternally

    /*var `x5t#S256`: String?
        get() = definedExternally
        set(value) = definedExternally*/
    var x5u: String?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(propName: String): Any?

    @nativeSetter
    operator fun set(propName: String, value: Any)
}

external interface GetKeyFunction<T, T2> {
    @nativeInvoke
    operator fun invoke(protectedHeader: T, token: T2): dynamic /* Promise<dynamic /* KeyLike | Uint8Array */> | KeyLike | Uint8Array */
}

external interface FlattenedJWSInput {
    var header: JWSHeaderParameters?
        get() = definedExternally
        set(value) = definedExternally
    var payload: dynamic /* String | Uint8Array */
        get() = definedExternally
        set(value) = definedExternally
    var protected: String?
        get() = definedExternally
        set(value) = definedExternally
    var signature: String
}

external interface GeneralJWSInput {
    var payload: dynamic /* String | Uint8Array */
        get() = definedExternally
        set(value) = definedExternally
    var signatures: Array<Omit<FlattenedJWSInput, String /* "payload" */>>
}

external interface FlattenedJWS /*: Partial<FlattenedJWSInput>*/ {
    var payload: String
    var signature: String
}

external interface GeneralJWS {
    var payload: String
    var signatures: Array<Omit<FlattenedJWSInput, String /* "payload" */>>
}

external interface JoseHeaderParameters {
    var kid: String?
        get() = definedExternally
        set(value) = definedExternally
    var x5t: String?
        get() = definedExternally
        set(value) = definedExternally
    var x5c: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var x5u: String?
        get() = definedExternally
        set(value) = definedExternally
    var jku: String?
        get() = definedExternally
        set(value) = definedExternally
    var jwk: Pick<JWK, String? /* "kty" | "crv" | "x" | "y" | "e" | "n" */>?
        get() = definedExternally
        set(value) = definedExternally
    var typ: String?
        get() = definedExternally
        set(value) = definedExternally
    var cty: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface JWSHeaderParameters : JoseHeaderParameters {
    var alg: String?
        get() = definedExternally
        set(value) = definedExternally
    var b64: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var crit: Array<String>?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(propName: String): Any?

    @nativeSetter
    operator fun set(propName: String, value: Any)
}

external interface JWEKeyManagementHeaderParameters {
    var apu: Uint8Array?
        get() = definedExternally
        set(value) = definedExternally
    var apv: Uint8Array?
        get() = definedExternally
        set(value) = definedExternally
    var p2c: Number?
        get() = definedExternally
        set(value) = definedExternally
    var p2s: Uint8Array?
        get() = definedExternally
        set(value) = definedExternally
    var iv: Uint8Array?
        get() = definedExternally
        set(value) = definedExternally
    var epk: KeyLike?
        get() = definedExternally
        set(value) = definedExternally
}

external interface FlattenedJWE {
    var aad: String?
        get() = definedExternally
        set(value) = definedExternally
    var ciphertext: String
    var encrypted_key: String?
        get() = definedExternally
        set(value) = definedExternally
    var header: JWEHeaderParameters?
        get() = definedExternally
        set(value) = definedExternally
    var iv: String
    var protected: String?
        get() = definedExternally
        set(value) = definedExternally
    var tag: String
    var unprotected: JWEHeaderParameters?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GeneralJWE : Omit<FlattenedJWE, String /* "encrypted_key" | "header" */> {
    var recipients: Array<Pick<FlattenedJWE, String /* "encrypted_key" | "header" */>>
}

external interface JWEHeaderParameters : JoseHeaderParameters {
    var alg: String?
        get() = definedExternally
        set(value) = definedExternally
    var enc: String?
        get() = definedExternally
        set(value) = definedExternally
    var crit: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var zip: String?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(propName: String): Any?

    @nativeSetter
    operator fun set(propName: String, value: Any)
}

external interface `T$1` {
    @nativeGetter
    operator fun get(propName: String): Boolean?

    @nativeSetter
    operator fun set(propName: String, value: Boolean)
}

external interface CritOption {
    var crit: `T$1`?
        get() = definedExternally
        set(value) = definedExternally
}

external interface DecryptOptions : CritOption {
    var keyManagementAlgorithms: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var contentEncryptionAlgorithms: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var inflateRaw: InflateFunction?
        get() = definedExternally
        set(value) = definedExternally
    var maxPBES2Count: Number?
        get() = definedExternally
        set(value) = definedExternally
}

external interface DeflateOption {
    var deflateRaw: DeflateFunction?
        get() = definedExternally
        set(value) = definedExternally
}

external interface EncryptOptions : CritOption, DeflateOption

external interface JWTClaimVerificationOptions {
    var audience: dynamic /* String? | Array<String>? */
        get() = definedExternally
        set(value) = definedExternally
    var clockTolerance: dynamic /* String? | Number? */
        get() = definedExternally
        set(value) = definedExternally
    var issuer: dynamic /* String? | Array<String>? */
        get() = definedExternally
        set(value) = definedExternally
    var maxTokenAge: dynamic /* String? | Number? */
        get() = definedExternally
        set(value) = definedExternally
    var subject: String?
        get() = definedExternally
        set(value) = definedExternally
    var typ: String?
        get() = definedExternally
        set(value) = definedExternally
    var currentDate: Date?
        get() = definedExternally
        set(value) = definedExternally
    var requiredClaims: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
}

external interface VerifyOptions : CritOption {
    var algorithms: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
}

external interface SignOptions : CritOption

external interface JWTPayload {
    var iss: String?
        get() = definedExternally
        set(value) = definedExternally
    var sub: String?
        get() = definedExternally
        set(value) = definedExternally
    var aud: dynamic /* String? | Array<String>? */
        get() = definedExternally
        set(value) = definedExternally
    var jti: String?
        get() = definedExternally
        set(value) = definedExternally
    var nbf: Number?
        get() = definedExternally
        set(value) = definedExternally
    var exp: Number?
        get() = definedExternally
        set(value) = definedExternally
    var iat: Number?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(propName: String): Any?

    @nativeSetter
    operator fun set(propName: String, value: Any)
}

external interface DeflateFunction {
    @nativeInvoke
    operator fun invoke(input: Uint8Array): Promise<Uint8Array>
}

external interface InflateFunction {
    @nativeInvoke
    operator fun invoke(input: Uint8Array): Promise<Uint8Array>
}

external interface FlattenedDecryptResult {
    var additionalAuthenticatedData: Uint8Array?
        get() = definedExternally
        set(value) = definedExternally
    var plaintext: Uint8Array
    var protectedHeader: JWEHeaderParameters?
        get() = definedExternally
        set(value) = definedExternally
    var sharedUnprotectedHeader: JWEHeaderParameters?
        get() = definedExternally
        set(value) = definedExternally
    var unprotectedHeader: JWEHeaderParameters?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GeneralDecryptResult : FlattenedDecryptResult

external interface CompactDecryptResult {
    var plaintext: Uint8Array
    var protectedHeader: CompactJWEHeaderParameters
}

external interface FlattenedVerifyResult {
    var payload: Uint8Array
    var protectedHeader: JWSHeaderParameters?
        get() = definedExternally
        set(value) = definedExternally
    var unprotectedHeader: JWSHeaderParameters?
        get() = definedExternally
        set(value) = definedExternally
}

external interface GeneralVerifyResult : FlattenedVerifyResult

external interface CompactVerifyResult {
    var payload: Uint8Array
    var protectedHeader: CompactJWSHeaderParameters
}

external interface JWTVerifyResult {
    var payload: JWTPayload
    var protectedHeader: JWTHeaderParameters
}

external interface JWTDecryptResult {
    var payload: JWTPayload
    var protectedHeader: CompactJWEHeaderParameters
}

external interface ResolvedKey<T : KeyLike> {
    var key: dynamic /* T | Uint8Array */
        get() = definedExternally
        set(value) = definedExternally
}

external interface CompactJWSHeaderParameters : JWSHeaderParameters

external interface JWTHeaderParameters : CompactJWSHeaderParameters {
    override var b64: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface CompactJWEHeaderParameters : JWEHeaderParameters

external interface JSONWebKeySet {
    var keys: Array<JWK>
}
