@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

import org.w3c.dom.url.URL
import tsstdlib.Record
import kotlin.js.Promise

external interface RemoteJWKSetOptions {
    var timeoutDuration: Number?
        get() = definedExternally
        set(value) = definedExternally
    var cooldownDuration: Number?
        get() = definedExternally
        set(value) = definedExternally
    var cacheMaxAge: dynamic /* Number? | Any? */
        get() = definedExternally
        set(value) = definedExternally
    var agent: Any?
        get() = definedExternally
        set(value) = definedExternally
    var headers: Record<String, String>?
        get() = definedExternally
        set(value) = definedExternally
}

external fun <T : KeyLike> createRemoteJWKSet(
    url: URL,
    options: RemoteJWKSetOptions = definedExternally
): (protectedHeader: JWSHeaderParameters, token: FlattenedJWSInput) -> Promise<T>
