@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

open external class ProduceJWT(payload: JWTPayload) {
    open var _payload: JWTPayload
    open fun setIssuer(issuer: String): ProduceJWT /* this */
    open fun setSubject(subject: String): ProduceJWT /* this */
    open fun setAudience(audience: String): ProduceJWT /* this */
    open fun setAudience(audience: Array<String>): ProduceJWT /* this */
    open fun setJti(jwtId: String): ProduceJWT /* this */
    open fun setNotBefore(input: Number): ProduceJWT /* this */
    open fun setNotBefore(input: String): ProduceJWT /* this */
    open fun setExpirationTime(input: Number): ProduceJWT /* this */
    open fun setExpirationTime(input: String): ProduceJWT /* this */
    open fun setIssuedAt(input: Number = definedExternally): ProduceJWT /* this */
}
