package registrars

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DidJwkRegistrarTest : DidRegistrarTestBase(DidJwkRegistrar()) {

    @ParameterizedTest
    @MethodSource
    override fun `given did options with no key when register then returns a valid did result`(
        options: DidCreateOptions,
        registrarDidAssertion: registrarDidAssertion,
    ) {
        super.`given did options with no key when register then returns a valid did result`(options, registrarDidAssertion)
    }

    @ParameterizedTest
    @MethodSource
    override fun `given did options and key when register with key then returns a valid did result`(
        key: Key,
        options: DidCreateOptions,
        registrarKeyAssertion: registrarKeyAssertion,
    ) {
        super.`given did options and key when register with key then returns a valid did result`(key, options, registrarKeyAssertion)
    }

    companion object {

        @JvmStatic
        fun `given did options with no key when register then returns a valid did result`(): Stream<Arguments> =
            Stream.of(
                arguments(DidJwkCreateOptions(KeyType.Ed25519), ed25519DidAssertions),
                arguments(DidJwkCreateOptions(KeyType.RSA), rsaDidAssertions),
                arguments(DidJwkCreateOptions(KeyType.RSA3072), rsaDidAssertions),
                arguments(DidJwkCreateOptions(KeyType.RSA4096), rsaDidAssertions),
                arguments(DidJwkCreateOptions(KeyType.secp256k1), secpDidAssertions),
                arguments(DidJwkCreateOptions(KeyType.secp256r1), secpDidAssertions),
                arguments(DidJwkCreateOptions(KeyType.secp384r1), secpDidAssertions),
                arguments(DidJwkCreateOptions(KeyType.secp521r1), secpDidAssertions),
            )

        @JvmStatic
        fun `given did options and key when register with key then returns a valid did result`(): Stream<Arguments> =
            keyTestData(secpKeyAssertions, ed25519KeyAssertions, rsaKeyAssertions)

        private fun keyTestData(
            secpAssertions: registrarKeyAssertion,
            ed25519Assertions: registrarKeyAssertion,
            rsaAssertions: registrarKeyAssertion,
        ) = Stream.of(
            //ed25519
            arguments(
                runBlocking { JWKKey.generate(KeyType.Ed25519) },
                DidJwkCreateOptions(),
                ed25519Assertions
            ),
            //rsa
            arguments(
                runBlocking { JWKKey.generate(KeyType.RSA) },
                DidJwkCreateOptions(),
                rsaAssertions
            ),
            //rsa3072
            arguments(
                runBlocking { JWKKey.generate(KeyType.RSA3072) },
                DidJwkCreateOptions(),
                rsaAssertions
            ),
            //rsa4096
            arguments(
                runBlocking { JWKKey.generate(KeyType.RSA4096) },
                DidJwkCreateOptions(),
                rsaAssertions
            ),
            //secp256k1
            arguments(
                runBlocking { JWKKey.generate(KeyType.secp256k1) },
                DidJwkCreateOptions(),
                secpAssertions
            ),
            //secp256r1
            arguments(
                runBlocking { JWKKey.generate(KeyType.secp256r1) },
                DidJwkCreateOptions(),
                secpAssertions
            ),
            //secp384r1
            arguments(
                runBlocking { JWKKey.generate(KeyType.secp384r1) },
                DidJwkCreateOptions(),
                secpAssertions
            ),
            //secp521r1
            arguments(
                runBlocking { JWKKey.generate(KeyType.secp521r1) },
                DidJwkCreateOptions(),
                secpAssertions
            ),
        )
    }
}
