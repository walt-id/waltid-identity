@file:Suppress("PropertyName", "unused", "PackageDirectoryMismatch")

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
