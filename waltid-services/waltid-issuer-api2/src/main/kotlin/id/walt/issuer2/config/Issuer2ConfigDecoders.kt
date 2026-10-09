package id.walt.issuer2.config

import id.walt.openid4vci.metadata.issuer.signing.SignedMetadataConfig

import com.sksamuel.hoplite.ArrayNode
import com.sksamuel.hoplite.BooleanNode
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.DoubleNode
import com.sksamuel.hoplite.LongNode
import com.sksamuel.hoplite.MapNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.NullNode
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.Undefined
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.flatMap
import com.sksamuel.hoplite.fp.Validated
import id.walt.commons.config.ConfigManager
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationConfig
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifierConfig
import id.walt.openid4vci.proofs.attestation.KeyAttestationConfig
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

fun registerIssuer2ConfigDecoders() {
    // Decode through the primary constructor. Hoplite's reflective decoder can
    // otherwise choose the legacy constructor and silently discard newer fields.
    ConfigManager.registerCustomDecoder(Issuer2ServiceConfigDecoder())
    ConfigManager.registerCustomDecoder(
        Issuer2KotlinxConfigDecoder(SignedMetadataConfig::class, SignedMetadataConfig.serializer(), ignoreUnknownKeys = false),
    )
    ConfigManager.registerCustomDecoder(
        Issuer2KotlinxConfigDecoder(KeyAttestationConfig::class, KeyAttestationConfig.serializer()),
    )
    ConfigManager.registerCustomDecoder(
        Issuer2KotlinxConfigDecoder(SdJwtVcTypeMetadataDraft04::class, SdJwtVcTypeMetadataDraft04.serializer()),
    )
    ConfigManager.registerCustomDecoder(
        Issuer2KotlinxConfigDecoder(
            SDMap::class,
            SDMap.serializer(),
        ),
    )
    ConfigManager.registerCustomDecoder(
        Issuer2KotlinxConfigDecoder(
            ClientAuthenticationConfig::class,
            ClientAuthenticationConfig.serializer(),
        ),
    )
    ConfigManager.registerCustomDecoder(
        Issuer2KotlinxConfigDecoder(
            ClientAttestationVerifierConfig::class,
            ClientAttestationVerifierConfig.serializer(),
        ),
    )
    ConfigManager.registerCustomDecoder(
        Issuer2KotlinxConfigDecoder(
            JsonObjectToCborMappingConfig::class,
            JsonObjectToCborMappingConfig.serializer(),
        ),
    )
}

private class Issuer2KotlinxConfigDecoder<T : Any>(
    private val supportedClass: KClass<T>,
    private val serializer: KSerializer<T>,
    ignoreUnknownKeys: Boolean = true,
) : Decoder<T> {
    private val json = Json {
        this.ignoreUnknownKeys = ignoreUnknownKeys
        explicitNulls = false
    }

    override fun supports(type: KType): Boolean =
        type.classifier == supportedClass

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<T> =
        try {
            val element = node.toJsonElement()
            Validated.Valid(json.decodeFromJsonElement(serializer, element))
        } catch (_: Exception) {
            Validated.Invalid(
                if (supportedClass == SignedMetadataConfig::class) {
                    ConfigFailure.Generic("Invalid signedMetadata configuration: use signingMethod with static-jwk (inline jwk), x509-chain (inline privateKeyPem and certificateChainPem list), or key-reference (reference and optional x5cReferences list)")
                } else ConfigFailure.DecodeError(node, type),
            )
        }

    private fun Node.toJsonElement(): JsonElement = when (this) {
        is MapNode -> JsonObject(map.mapValues { it.value.toJsonElement() })
        is ArrayNode -> JsonArray(elements.map { it.toJsonElement() })
        is StringNode -> JsonPrimitive(value)
        is BooleanNode -> JsonPrimitive(value)
        is LongNode -> JsonPrimitive(value)
        is DoubleNode -> JsonPrimitive(value)
        is NullNode -> JsonNull
        Undefined -> JsonNull
    }
}

/** Keeps Hoplite field decoding and aliases while excluding the ABI compatibility constructor. */
private class Issuer2ServiceConfigDecoder : Decoder<Issuer2ServiceConfig> {
    override fun supports(type: KType): Boolean = type.classifier == Issuer2ServiceConfig::class

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<Issuer2ServiceConfig> {
        if (node.atKey("metadataSigning") !is Undefined || node.atKey("metadata-signing") !is Undefined) {
            return Validated.Invalid(ConfigFailure.Generic("metadataSigning is unsupported; configure signedMetadata.signingMethod"))
        }
        val constructor = requireNotNull(Issuer2ServiceConfig::class.primaryConstructor)
        val args = mutableMapOf<KParameter, Any?>()
        for (param in constructor.parameters) {
            val names = context.paramMappers.flatMap { it.map(param, constructor, Issuer2ServiceConfig::class) }
            val nodes = names.map { name ->
                var found = node.atKey(name)
                if (found is Undefined) found = node.atKey(
                    context.nodeTransformers.fold(name) { value, transformer -> transformer.transformPathElement(value) },
                )
                if (found is Undefined) found = node.atSourceKey(name)
                context.usedPaths.add(found.path)
                found
            }
            val value = nodes.firstOrNull { it !is Undefined } ?: Undefined
            if (param.isOptional && value is Undefined) continue
            val decoded = context.decoder(param).flatMap { decoder ->
                runBlocking {
                    context.resolvers.resolve(value, requireNotNull(param.name), Issuer2ServiceConfig::class, context)
                        .flatMap { decoder.decode(it, param.type, context) }
                }
            }
            when (decoded) {
                is Validated.Invalid -> return decoded
                is Validated.Valid -> {
                    args[param] = decoded.value
                    context.used(value, param.type, decoded.value)
                }
            }
        }
        return try {
            Validated.Valid(constructor.callBy(args))
        } catch (_: Exception) {
            Validated.Invalid(ConfigFailure.DecodeError(node, type))
        }
    }
}
