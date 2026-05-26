package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking

data class Issuer2ServiceConfig(
    val baseUrl: String,
    val ciTokenKey: String = runBlocking { KeySerialization.serializeKey(JWKKey.generate(KeyType.secp256r1)) },
) : WaltConfig()