@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

open external class SignJWT(payload: JWTPayload) : ProduceJWT {
    open var _protectedHeader: Any
    open fun setProtectedHeader(protectedHeader: JWTHeaderParameters): SignJWT /* this */
    open fun sign(key: KeyLike, options: SignOptions = definedExternally): Promise<String>
    open fun sign(key: KeyLike): Promise<String>
    open fun sign(key: Uint8Array, options: SignOptions = definedExternally): Promise<String>
    open fun sign(key: Uint8Array): Promise<String>
}
