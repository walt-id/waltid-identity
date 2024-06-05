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
        assert: registrarDidAssertion
    ) {
        super.`given did options with no key when register then returns a valid did result`(options, assert)
    }

    @ParameterizedTest
    @MethodSource
    override fun `given did options and key when register with key then returns a valid did result`(
        key: Key,
        options: DidCreateOptions,
        assert: registrarKeyAssertion
    ) {
        super.`given did options and key when register with key then returns a valid did result`(key, options, assert)
    }

    companion object {

        @JvmStatic
        fun `given did options with no key when register then returns a valid did result`(): Stream<Arguments> =
            Stream.of(
                arguments(DidJwkCreateOptions(KeyType.Ed25519), ed25519DidAssertions),
                arguments(DidJwkCreateOptions(KeyType.RSA), rsaDidAssertions),
                arguments(DidJwkCreateOptions(KeyType.secp256k1), secp256DidAssertions),
                arguments(DidJwkCreateOptions(KeyType.secp256r1), secp256DidAssertions),
            )

        @JvmStatic
        fun `given did options and key when register with key then returns a valid did result`(): Stream<Arguments> =
            keyTestData(secp256KeyAssertions, ed25519KeyAssertions, rsaKeyAssertions)

        private fun keyTestData(
            secpAssertions: registrarKeyAssertion,
            ed25519Assertions: registrarKeyAssertion,
            rsaAssertions: registrarKeyAssertion
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
        )
    }
}
