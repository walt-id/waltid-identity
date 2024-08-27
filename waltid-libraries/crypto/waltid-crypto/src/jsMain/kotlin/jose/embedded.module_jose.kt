@file:Suppress("PropertyName", "unused", "PackageDirectoryMismatch")

import kotlin.js.Promise

external fun <T : KeyLike> EmbeddedJWK(
    protectedHeader: JWSHeaderParameters = definedExternally,
    token: FlattenedJWSInput = definedExternally
): Promise<T>
