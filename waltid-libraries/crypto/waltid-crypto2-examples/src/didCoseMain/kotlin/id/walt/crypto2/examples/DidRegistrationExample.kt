package id.walt.crypto2.examples

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.registrar.dids.DidCreateOptions

data class DidRegistrationResult(
    val didKeyLength: Int,
    val didJwkLength: Int,
)

object DidRegistrationExample {
    suspend fun run(output: ExampleOutput): DidRegistrationResult {
        output("=== Native did:key + did:jwk registration ===")
        val provider = CryptographySoftwareKeyProvider()
        val runtime = CryptoRuntime(softwareProviders = listOf(provider))
        output("1. Create provider=${provider.id.value} and generate P-256 SIGN|VERIFY key")
        val key = runtime.generateSoftwareKey(
            request = GenerateSoftwareKeyRequest(
                id = KeyId("did-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

        // Native crypto2 registrars consume the key directly and export public material only.
        val didKey = Crypto2DidService.registerByKey(
            method = "key",
            key = key,
            options = DidCreateOptions(method = "key", config = emptyMap<String, Any?>()),
        )
        output("2. Register did:key from public P-256 material")
        val didJwk = Crypto2DidService.registerByKey(
            method = "jwk",
            key = key,
            options = DidCreateOptions(method = "jwk", config = emptyMap<String, Any?>()),
        )
        output("3. Register did:jwk from the public JWK")

        // DIDs are public, but lengths and method prefixes keep presentation output compact.
        check(didKey.did.startsWith("did:key:"))
        check(didJwk.did.startsWith("did:jwk:"))
        output("4. Registration result: did:key length=${didKey.did.length}, did:jwk length=${didJwk.did.length}")
        output("   Private key material is not included in either DID")
        return DidRegistrationResult(didKey.did.length, didJwk.did.length)
    }
}
