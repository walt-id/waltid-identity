@file:Suppress("PropertyName", "unused", "PackageDirectoryMismatch")

import kotlin.js.Promise

external fun <T : KeyLike> createLocalJWKSet(jwks: JSONWebKeySet): (protectedHeader: JWSHeaderParameters, token: FlattenedJWSInput) -> Promise<T>
