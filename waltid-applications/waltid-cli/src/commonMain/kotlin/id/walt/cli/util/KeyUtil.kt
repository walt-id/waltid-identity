package id.walt.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.mordant.rendering.TextStyles
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwkMetadata
import id.walt.crypto2.jose.JwkOperation
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.defaultJwsAlgorithm
import id.walt.crypto2.jose.supportsJwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.keys.inferKeySpec
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.random.Random

class KeyUtil(private val cmd: CliktCommand? = null) {

    suspend fun getKey(keyFile: File?): Key {
        keyFile ?: return generateDefaultKey()

        try {
            return importJwk(keyFile.readText())
        } catch (cause: Exception) {
            throw InvalidFileFormat(keyFile.name, cause.message ?: "Invalid JWK")
        }
    }

    private suspend fun generateDefaultKey(): Key {
        val msg1 = TextStyles.dim("Key not provided. Let's generate a new one...")
        printit(msg1)
        val key = generate(KeySpec.Edwards(EdwardsCurve.ED25519))
        val msg2 = TextStyles.dim("Key generated with thumbprint ${thumbprint(key)}")
        printit(msg2)

        return key
    }

    private fun printit(msg: String) {
        if (cmd != null) cmd.echo(msg)
        else println(msg)
    }

    companion object {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()) + platformSoftwareKeyProviders())
        private val prettyJson = Json { prettyPrint = true }

        suspend fun generate(spec: KeySpec): Key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("generated-${Random.nextLong().toULong()}"),
                spec = spec,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )

        suspend fun importJwk(content: String, keyId: String? = null): Key {
            val json = Json.parseToJsonElement(content) as? JsonObject
                ?: throw IllegalArgumentException("JWK must be a JSON object")
            val encoded = EncodedKey.Jwk(
                data = BinaryData(Json.encodeToString(json).encodeToByteArray()),
                privateMaterial = Jwk.containsPrivateMaterial(json),
            )
            val metadata = Jwk.metadata(encoded)
            metadata.algorithm?.let { algorithm ->
                require(encoded.inferKeySpec().supportsJwsAlgorithm(JwsAlgorithm.parse(algorithm))) {
                    "JWK algorithm $algorithm is incompatible with its key material"
                }
            }
            val usages = when {
                encoded.privateMaterial && metadata.operations == null -> setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
                encoded.privateMaterial -> metadata.operations.orEmpty().mapTo(mutableSetOf()) { operation ->
                    when (operation) {
                        JwkOperation.SIGN -> KeyUsage.SIGN
                        JwkOperation.VERIFY -> KeyUsage.VERIFY
                        else -> throw IllegalArgumentException("CLI signing keys only support sign and verify key operations")
                    }
                }
                else -> setOf(KeyUsage.VERIFY)
            }
            val id = keyId ?: metadata.keyId ?: Jwk.sha256Thumbprint(encoded)
            return runtime.restore(encoded.toStoredSoftwareKey(KeyId(id), usages))
        }

        suspend fun restore(material: EncodedKey, spec: KeySpec, keyId: String = "imported-key"): Key =
            runtime.restore(
                StoredKey.Software(
                    version = StoredKey.CURRENT_VERSION,
                    id = KeyId(keyId),
                    spec = spec,
                    usages = if (material is EncodedKey.SpkiDer) setOf(KeyUsage.VERIFY)
                    else setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                    material = material,
                )
            )

        suspend fun privateJwk(key: Key): EncodedKey.Jwk =
            requireNotNull(key.capabilities.privateKeyExporter) { "Key has no exportable private material" }
                .exportPrivateKey(KeyEncodingFormat.JWK) as EncodedKey.Jwk

        suspend fun publicJwk(key: Key): EncodedKey.Jwk =
            requireNotNull(key.capabilities.publicKeyExporter) { "Key has no exportable public material" }
                .exportPublicKey(KeyEncodingFormat.JWK) as EncodedKey.Jwk

        suspend fun thumbprint(key: Key): String = Jwk.sha256Thumbprint(publicJwk(key))

        suspend fun samePublicIdentity(first: Key, second: Key): Boolean = thumbprint(first) == thumbprint(second)

        suspend fun exportJwk(key: Key, private: Boolean): String {
            val exported = if (private) privateJwk(key) else publicJwk(key)
            val withMetadata = Jwk.withMetadata(
                exported,
                JwkMetadata(
                    keyId = thumbprint(key),
                    algorithm = key.spec.defaultJwsAlgorithm().identifier,
                ),
            )
            return prettyJson.encodeToString(Jwk.parse(withMetadata))
        }

        suspend fun didContainsKey(did: String, key: Key): Boolean {
            DidService.minimalInit()
            val expected = thumbprint(key)
            val document = Crypto2DidService.resolve(did).getOrThrow()
            val methods = document["verificationMethod"] as? JsonArray ?: return false
            return methods.any { element ->
                val jwk = (element as? JsonObject)?.get("publicKeyJwk") as? JsonObject ?: return@any false
                val encoded = EncodedKey.Jwk(
                    data = BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
                    privateMaterial = false,
                )
                Jwk.sha256Thumbprint(encoded) == expected
            }
        }

        suspend fun resolveDidVerificationKey(did: String, keyId: String?): Key {
            DidService.minimalInit()
            val document = Crypto2DidService.resolve(did).getOrThrow()
            val methods = document["verificationMethod"] as? JsonArray
                ?: throw IllegalArgumentException("DID document has no verification methods: $did")
            val candidates = methods.mapNotNull { element ->
                val method = element as? JsonObject ?: return@mapNotNull null
                val methodId = (method["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                if (keyId != null && methodId != keyId && methodId.substringAfter('#') != keyId.substringAfter('#')) {
                    return@mapNotNull null
                }
                val jwk = method["publicKeyJwk"] as? JsonObject ?: return@mapNotNull null
                val normalized = if (jwk["crv"] == JsonPrimitive("secp256k1") && "alg" !in jwk) {
                    JsonObject(jwk + ("alg" to JsonPrimitive("ES256K")))
                } else jwk
                importJwk(Json.encodeToString(normalized), methodId)
            }
            require(candidates.size == 1) {
                if (keyId == null) "DID must resolve to exactly one supported verification key"
                else "DID must resolve exactly one verification key matching kid $keyId"
            }
            return candidates.single()
        }
    }
}
