@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

open external class EncryptJWT(payload: JWTPayload) : ProduceJWT {
    open var _cek: Any
    open var _iv: Any
    open var _keyManagementParameters: Any
    open var _protectedHeader: Any
    open var _replicateIssuerAsHeader: Any
    open var _replicateSubjectAsHeader: Any
    open var _replicateAudienceAsHeader: Any
    open fun setProtectedHeader(protectedHeader: CompactJWEHeaderParameters): EncryptJWT /* this */
    open fun setKeyManagementParameters(parameters: JWEKeyManagementHeaderParameters): EncryptJWT /* this */
    open fun setContentEncryptionKey(cek: Uint8Array): EncryptJWT /* this */
    open fun setInitializationVector(iv: Uint8Array): EncryptJWT /* this */
    open fun replicateIssuerAsHeader(): EncryptJWT /* this */
    open fun replicateSubjectAsHeader(): EncryptJWT /* this */
    open fun replicateAudienceAsHeader(): EncryptJWT /* this */
    open fun encrypt(key: KeyLike, options: EncryptOptions = definedExternally): Promise<String>
    open fun encrypt(key: KeyLike): Promise<String>
    open fun encrypt(key: Uint8Array, options: EncryptOptions = definedExternally): Promise<String>
    open fun encrypt(key: Uint8Array): Promise<String>
}
