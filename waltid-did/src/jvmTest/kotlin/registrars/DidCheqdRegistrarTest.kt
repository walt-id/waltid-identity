package registrars

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidCheqdCreateOptions
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.cheqd.DidCheqdRegistrar
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DidCheqdRegistrarTest : DidRegistrarTestBase(DidCheqdRegistrar()) {

    /* FIXME: Re-enable CHEQD tests (their API is currently broken)
    @ParameterizedTest*/
    @MethodSource
    override fun `given did options with no key when register then returns a valid did result`(
        options: DidCreateOptions,
        assert: registrarDidAssertion
    ) {
        super.`given did options with no key when register then returns a valid did result`(options, assert)
    }

    /* FIXME: Re-enable CHEQD tests (their API is currently broken)
    @ParameterizedTest*/
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
                arguments(DidCheqdCreateOptions(network = "testnet"), ed25519DidAssertions),
            )

        @JvmStatic
        fun `given did options and key when register with key then returns a valid did result`(): Stream<Arguments> =
            keyTestData(ed25519KeyAssertions)

        private fun keyTestData(
            ed25519Assertions: registrarKeyAssertion,
        ) = Stream.of(
            //ed25519
            arguments(
                runBlocking { JWKKey.generate(KeyType.Ed25519) },
                DidCheqdCreateOptions(network = "testnet"),
                ed25519Assertions
            ),
        )
    }
}
