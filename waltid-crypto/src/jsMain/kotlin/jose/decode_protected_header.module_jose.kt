@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

external fun decodeProtectedHeader(token: String?): JWSHeaderParameters /* JWSHeaderParameters & JWEHeaderParameters */

external fun decodeProtectedHeader(token: Any?): JWSHeaderParameters /* JWSHeaderParameters & JWEHeaderParameters */
