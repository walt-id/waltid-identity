@file:Suppress("PropertyName", "unused", "PackageDirectoryMismatch")

import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

external interface JWTDecryptOptions : DecryptOptions, JWTClaimVerificationOptions

external interface JWTDecryptGetKey : GetKeyFunction<CompactJWEHeaderParameters, FlattenedJWE>

external fun jwtDecrypt(jwt: String, key: KeyLike, options: JWTDecryptOptions = definedExternally): Promise<JWTDecryptResult>

external fun jwtDecrypt(jwt: String, key: KeyLike): Promise<JWTDecryptResult>

external fun jwtDecrypt(jwt: String, key: Uint8Array, options: JWTDecryptOptions = definedExternally): Promise<JWTDecryptResult>

external fun jwtDecrypt(jwt: String, key: Uint8Array): Promise<JWTDecryptResult>

external fun jwtDecrypt(jwt: Uint8Array, key: KeyLike, options: JWTDecryptOptions = definedExternally): Promise<JWTDecryptResult>

external fun jwtDecrypt(jwt: Uint8Array, key: KeyLike): Promise<JWTDecryptResult>

external fun jwtDecrypt(jwt: Uint8Array, key: Uint8Array, options: JWTDecryptOptions = definedExternally): Promise<JWTDecryptResult>

external fun jwtDecrypt(jwt: Uint8Array, key: Uint8Array): Promise<JWTDecryptResult>

external fun <T : KeyLike> jwtDecrypt(
    jwt: String,
    getKey: JWTDecryptGetKey,
    options: JWTDecryptOptions = definedExternally
): Promise<JWTDecryptResult /* JWTDecryptResult & ResolvedKey<T> */>

external fun <T : KeyLike> jwtDecrypt(
    jwt: String,
    getKey: JWTDecryptGetKey
): Promise<JWTDecryptResult /* JWTDecryptResult & ResolvedKey<T> */>

external fun <T : KeyLike> jwtDecrypt(
    jwt: Uint8Array,
    getKey: JWTDecryptGetKey,
    options: JWTDecryptOptions = definedExternally
): Promise<JWTDecryptResult /* JWTDecryptResult & ResolvedKey<T> */>

external fun <T : KeyLike> jwtDecrypt(
    jwt: Uint8Array,
    getKey: JWTDecryptGetKey
): Promise<JWTDecryptResult /* JWTDecryptResult & ResolvedKey<T> */>
