@file:Suppress("PropertyName", "unused", "PackageDirectoryMismatch")

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
