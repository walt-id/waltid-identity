package registrars

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DidKeyRegistrarTest : DidRegistrarTestBase(DidKeyRegistrar()) {

    @ParameterizedTest
    @MethodSource
    override fun `given did options with no key when register then returns a valid did result`(
        options: DidCreateOptions,
        assert: registrarDidAssertion,
    ) {
        super.`given did options with no key when register then returns a valid did result`(options, assert)
    }

    @ParameterizedTest
    @MethodSource
    override fun `given did options and key when register with key then returns a valid did result`(
        key: Key,
        options: DidCreateOptions,
        assert: registrarKeyAssertion,
    ) {
        super.`given did options and key when register with key then returns a valid did result`(key, options, assert)
    }

    companion object {

        @JvmStatic
        fun `given did options with no key when register then returns a valid did result`(): Stream<Arguments> =
            Stream.of(
                arguments(DidKeyCreateOptions(KeyType.Ed25519), ed25519DidAssertions),
                arguments(DidKeyCreateOptions(KeyType.Ed25519, useJwkJcsPub = true), ed25519DidAssertions),
                arguments(DidKeyCreateOptions(KeyType.RSA), rsaDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.RSA, useJwkJcsPub = true), rsaDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.RSA3072), rsaDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.RSA3072, useJwkJcsPub = true), rsaDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.RSA4096), rsaDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.RSA4096, useJwkJcsPub = true), rsaDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.secp256k1), secpDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.secp256k1, useJwkJcsPub = true), secpDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.secp256r1), secpDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.secp256r1, useJwkJcsPub = true), secpDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.secp384r1), secpDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.secp384r1, useJwkJcsPub = true), secpDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.secp521r1), secpDidAssertions),
                arguments(DidKeyCreateOptions(KeyType.secp521r1, useJwkJcsPub = true), secpDidAssertions),
            )

        @JvmStatic
        fun `given did options and key when register with key then returns a valid did result`(): Stream<Arguments> =
            Stream.of(
                //ed25519 without jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.Ed25519) },
                    DidKeyCreateOptions(),
                    ed25519KeyAssertions
                ),
                //ed25519 with jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.Ed25519) },
                    DidKeyCreateOptions(useJwkJcsPub = true),
                    ed25519KeyAssertions
                ),
                //rsa without jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.RSA) },
                    DidKeyCreateOptions(),
                    rsaKeyAssertions
                ),
                //rsa with jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.RSA) },
                    DidKeyCreateOptions(useJwkJcsPub = true),
                    rsaKeyAssertions
                ),
                //rsa3072 without jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.RSA3072) },
                    DidKeyCreateOptions(),
                    rsaKeyAssertions
                ),
                //rsa3072 with jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.RSA3072) },
                    DidKeyCreateOptions(useJwkJcsPub = true),
                    rsaKeyAssertions
                ),
                //rsa4096 without jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.RSA4096) },
                    DidKeyCreateOptions(),
                    rsaKeyAssertions
                ),
                //rsa4096 with jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.RSA4096) },
                    DidKeyCreateOptions(useJwkJcsPub = true),
                    rsaKeyAssertions
                ),
                //secp256k1 without jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.secp256k1) },
                    DidKeyCreateOptions(),
                    secpKeyAssertions
                ),
                //secp256k1 with jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.secp256k1) },
                    DidKeyCreateOptions(useJwkJcsPub = true),
                    secpKeyAssertions
                ),
                //secp256r1 without jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.secp256r1) },
                    DidKeyCreateOptions(),
                    secpKeyAssertions
                ),
                //secp256r1 with jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.secp256r1) },
                    DidKeyCreateOptions(useJwkJcsPub = true),
                    secpKeyAssertions
                ),
                //secp384r1 without jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.secp384r1) },
                    DidKeyCreateOptions(),
                    secpKeyAssertions
                ),
                //secp384r1 with jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.secp384r1) },
                    DidKeyCreateOptions(useJwkJcsPub = true),
                    secpKeyAssertions
                ),
                //secp521r1 without jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.secp521r1) },
                    DidKeyCreateOptions(),
                    secpKeyAssertions
                ),
                //secp521r1 with jwk jcs pub encoding
                arguments(
                    runBlocking { JWKKey.generate(KeyType.secp521r1) },
                    DidKeyCreateOptions(useJwkJcsPub = true),
                    secpKeyAssertions
                ),
            )
    }
}
