@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

import kotlin.js.Promise

external interface GenerateSecretOptions {
    var extractable: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external fun <T : KeyLike> generateSecret(
    alg: String,
    options: GenerateSecretOptions = definedExternally
): Promise<dynamic /* T | Uint8Array */>
