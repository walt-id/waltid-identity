package registrars

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.did.dids.DidUtils
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import id.walt.did.utils.EncodingUtils
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DidWebRegistrarTest : DidRegistrarTestBase(DidWebRegistrar()){
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
                arguments(DidWebCreateOptions(domain = "localhost:3000", path = "", keyType = KeyType.Ed25519), webDidAssertions),
                arguments(DidWebCreateOptions(domain = "walt.id", path = "/prefix-test", keyType = KeyType.Ed25519), webDidAssertions),
                arguments(DidWebCreateOptions(domain = "walt.id", path = "no-prefix/test", keyType = KeyType.Ed25519), webDidAssertions),
                arguments(DidWebCreateOptions(domain = "walt.id", path = "", keyType = KeyType.Ed25519), webDidAssertions),
                arguments(DidWebCreateOptions(domain = "walt.id", path = "", keyType = KeyType.Ed25519), webDidAssertions),
            )

        @JvmStatic
        fun `given did options and key when register with key then returns a valid did result`(): Stream<Arguments> =
            Stream.of(
                //empty-path
                arguments(
                    runBlocking { LocalKey.generate(KeyType.Ed25519) },
                    DidWebCreateOptions(domain = "localhost:3000", path = "", keyType = KeyType.Ed25519),
                    webKeyAssertions
                ),
                //prefixed-path
                arguments(
                    runBlocking { LocalKey.generate(KeyType.Ed25519) },
                    DidWebCreateOptions(domain = "walt.id", path = "/prefix-test", keyType = KeyType.Ed25519),
                    webKeyAssertions
                ),
                //non-prefixed-path
                arguments(
                    runBlocking { LocalKey.generate(KeyType.Ed25519) },
                    DidWebCreateOptions(domain = "walt.id", path = "no-prefix/test", keyType = KeyType.Ed25519),
                    webKeyAssertions
                )
            )

        private val webDidAssertions: registrarDidAssertion = { result, options ->
            val did = result.did
            val domain = options.get<String>("domain")!!
            val path = options.get<String>("path")
            defaultDidAssertions(result, options)
            // assert [did identifier] and [domain + path] are identical
            assert(
                EncodingUtils.urlDecode(DidUtils.identifierFromDid(did)!!) ==
                        domain.plus(path?.replace("/", ":"))
            )
        }

        private val webKeyAssertions: registrarKeyAssertion = { result, options, key ->
            val did = result.did
            val domain = options.get<String>("domain")!!
            val path = options.get<String>("path")
            defaultKeyAssertions(result, options, key)
            // assert [did identifier] and [domain + path] are identical
            assert(
                EncodingUtils.urlDecode(DidUtils.identifierFromDid(did)!!) ==
                        domain.plus(path?.replace("/", ":"))
            )
        }
    }
}
