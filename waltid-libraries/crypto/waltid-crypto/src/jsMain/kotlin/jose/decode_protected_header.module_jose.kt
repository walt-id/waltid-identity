@file:Suppress("PropertyName", "unused", "PackageDirectoryMismatch")

external fun decodeProtectedHeader(token: String?): JWSHeaderParameters /* JWSHeaderParameters & JWEHeaderParameters */

external fun decodeProtectedHeader(token: Any?): JWSHeaderParameters /* JWSHeaderParameters & JWEHeaderParameters */
