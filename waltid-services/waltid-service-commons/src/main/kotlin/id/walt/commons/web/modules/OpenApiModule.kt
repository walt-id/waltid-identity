@file:OptIn(SealedSerializationApi::class, ExperimentalSerializationApi::class, ExperimentalTime::class)

package id.walt.commons.web.modules

import com.sksamuel.hoplite.simpleName
import id.walt.commons.config.statics.BuildConfig
import id.walt.commons.config.statics.ServiceConfig
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.*
import io.github.smiley4.ktoropenapi.config.descriptors.*
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorredoc.redoc
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.github.smiley4.schemakenerator.core.data.*
import io.github.smiley4.schemakenerator.reflection.analyzer.MinimalTypeData
import io.github.smiley4.schemakenerator.reflection.analyzer.ReflectionTypeAnalyzerModule
import io.github.smiley4.schemakenerator.serialization.analyzer.SerializationTypeAnalyzerModule
import io.github.smiley4.schemakenerator.serialization.data.InitialSerialDescriptorTypeData
import io.github.smiley4.schemakenerator.swagger.generator.SwaggerSchemaGenerationModule
import io.klogging.noCoLogger
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.Schema
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.descriptors.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

object OpenApiModule {

    private val logger = noCoLogger("OpenAPI")

    object OpenApiConfig {
        var customInfo: (InfoConfig.() -> Unit)? = null

        var custom: (OpenApiPluginConfig.() -> Unit)? = null
    }

    // Module
    @OptIn(ExperimentalUuidApi::class)
    fun Application.enable() {
        install(OpenApi) {

            schemas {
                val kotlinxGenerator = SchemaGenerator.kotlinx {
                    explicitNullTypes = false
                    customAnalyzer(ContextualSerializationTypeAnalyzerModule)
                    customAnalyzer(FixSealedClassInheritanceModule)
                    customGenerator(FixSealedClassInheritanceModule)
                    customGenerator(FixJsonCustomParameters)
                    overwrite(SchemaGenerator.TypeOverwrites.KotlinUuid())
                    overwrite(SchemaGenerator.TypeOverwrites.File())
                    overwrite(SchemaGenerator.TypeOverwrites.Instant())
                    overwrite(CustomTypeOverrides.KotlinxInstant())
                    overwrite(CustomTypeOverrides.JsonArray())
                    overwrite(CustomTypeOverrides.JsonObject())
                    overwrite(CustomTypeOverrides.JsonElement())
                    overwrite(CustomTypeOverrides.SdMap())
                    overwrite(CustomTypeOverrides.QuickFixPolymorphic())
                }
                val reflectionGenerator = SchemaGenerator.reflection {
                    explicitNullTypes = false
                    customGenerator(FixJsonCustomParameters)
                    overwrite(SchemaGenerator.TypeOverwrites.KotlinUuid())
                    overwrite(SchemaGenerator.TypeOverwrites.File())
                    overwrite(SchemaGenerator.TypeOverwrites.Instant())
                    overwrite(CustomTypeOverrides.KotlinxInstant())
                    overwrite(CustomTypeOverrides.JsonArray())
                    overwrite(CustomTypeOverrides.JsonObject())
                    overwrite(CustomTypeOverrides.JsonElement())
                    overwrite(CustomTypeOverrides.SdMap())
                }

                fun InitialTypeData.schemaName() =
                    when (this) {
                        is InitialKTypeData -> "${this.type} (KType)"
                        is InitialSerialDescriptorTypeData -> "${this.type.serialName} (Serialname)"
                        else -> error("Unknown data type $this")
                    }

                generator = { type ->
                    runCatching {
                        kotlinxGenerator.invoke(type)
                    }.recoverCatching {
                        logger.debug { "Failed kotlinx schema generation, trying reflection schema generation for: \"${type.schemaName()}\", due to: \"${it.message}\"." }
                        reflectionGenerator.invoke(type)
                    }.getOrThrow()
                }
            }

            examples {
                val kotlinxEncoder = ExampleEncoder.kotlinx()
                val reflectionEncoder = ExampleEncoder.internal()

                fun TypeDescriptor.typeName(): String = when (this) {
                    is SwaggerTypeDescriptor -> "${schema.name} ${schema.type} (SwaggerType)"
                    is KTypeDescriptor -> type.simpleName + "<" + type.arguments.joinToString { it.type?.simpleName.toString() } + "> (KType)"
                    is SerialTypeDescriptor -> "${descriptor.serialName} (SerialType)"
                    is AnyOfTypeDescriptor -> "Any of ${this.types.map { it.typeName() }} (AnyOfType)"
                    is ArrayTypeDescriptor -> "Array of ${this.type.typeName()} (ArrayType)"
                    is EmptyTypeDescriptor -> "Empty Type (EmptyType)"
                    is RefTypeDescriptor -> "$schemaId (RefType)"
                }

                exampleEncoder = { type, example ->
                    runCatching {
                        kotlinxEncoder.invoke(type, example)
                    }.recoverCatching {
                        logger.debug { "Failed kotlinx example encoding, trying internal example encoder for: \"${type?.typeName()}\", due to: \"${it.message}\"." }
                        reflectionEncoder.invoke(type, example)
                    }.getOrThrow()
                }
            }

            info {
                title = "${ServiceConfig.config.vendor} ${ServiceConfig.config.name}"
                version = BuildConfig.version
                description = """
                    Interact with the ${ServiceConfig.config.vendor} ${ServiceConfig.config.name}. Version is reported to be ${BuildConfig.version} and this service instance was started ${
                    Clock.System.now().roundToSecond()
                }.
                    Questions about anything here? Visit <a href='${ServiceConfig.config.supportUrl}'>support</a>.

                """.trimIndent().replace("\n", "<br/>")

                contact {
                    url = "https://walt.id"
                    name = "walt.id"
                    email = "office@walt.id"
                }
                license {
                    name = ServiceConfig.config.licenseName
                    url = ServiceConfig.config.licenseUrl
                }

                OpenApiConfig.customInfo?.invoke(this)
            }

            server {
                url = "/"
                description = "Development Server"
            }

            OpenApiConfig.custom?.invoke(this)

            externalDocs {
                url = "https://docs.walt.id"
                description = "docs.walt.id"
            }
        }

        routing {
            route("api.json") {
                openApi()
            }

            route("swagger") {
                swaggerUI("/api.json") {
                    filter = true
                    // onlineSpecValidator()
                }
            }

            route("redoc") {
                redoc("/api.json")
            }

            get("/", {
                // This is hidden, because in the api.json would be strange empty URL if
                // this is enabled.
                hidden = true
                summary = "Redirect to swagger interface for API documentation"
            }) {
                call.respondRedirect("swagger")
            }
        }
    }
}

private fun Instant.roundToSecond(): Instant =
    minus(nanosecondsOfSecond.nanoseconds)

/**
 * When schema is generated for a class which inherits from a sealed class, then an "anyOf" schema is generated
 * for the sealed class. Kotlin Json adds implicitly a type parameter to the child classes. This is not reflected
 * in the generated schema. This class should fix the problem.
 */
private object FixSealedClassInheritanceModule : SwaggerSchemaGenerationModule, SerializationTypeAnalyzerModule {
    const val gerneratorMarker = "FIX_INHERITANCE_MARKER"
    val childElementNames = mutableSetOf<String>()

    override fun applies(typeData: TypeData): Boolean {
        if (childElementNames.contains(typeData.identifyingName.full)) {
            return !typeData.annotations.any { a -> a.name.equals(gerneratorMarker) }
        }
        if (typeData.subtypes.isNotEmpty()
            && typeData.identifyingName.full.startsWith("id.walt.")
        ) {
            return !typeData.annotations.any { a -> a.name.equals(gerneratorMarker) }
        }
        return false
    }

    override fun generate(context: SwaggerSchemaGenerationModule.Context): Schema<*> {
        context.typeData.annotations.add(AnnotationData(gerneratorMarker, mutableMapOf()))
        val stringType = context.knownTypeData.first { it.identifyingName.full.equals("kotlin.String") }
        if (context.typeData.subtypes.isNotEmpty()) {
            context.typeData.subtypes.forEach { subTypeId ->
                val subType = context.knownTypeData.first { search -> search.id.id.equals(subTypeId.id) }
                subType.members.add(
                    MemberData(
                        name = "type",
                        type = stringType.id,
                        nullable = false,
                        optional = false,
                        visibility = Visibility.PUBLIC,
                        kind = MemberKind.PROPERTY,
                        annotations = mutableListOf()
                    )
                )
            }
        }
        val generated = context.generate(context.typeData)
        if (generated.anyOf != null) {
            generated.discriminator = Discriminator()
                .propertyName("type")
        } else if (childElementNames.contains(context.typeData.identifyingName.full)) {
            Schema<String>().let {
                it.`raw$ref`(stringType.id.id)
                it.nullable = false
                it.exampleSetFlag = false
                generated.properties.put("type", it)
            }
        }
        return generated
    }

    override fun applies(descriptor: SerialDescriptor): Boolean {
        if (descriptor.kind == PolymorphicKind.SEALED) {
            descriptor.elementDescriptors
                .filter { it.kind == SerialKind.CONTEXTUAL }
                .forEach {
                    childElementNames.addAll(it.elementNames)
                }
        }
        return false
    }

    override fun analyze(context: SerializationTypeAnalyzerModule.Context): WrappedTypeData {
        TODO("Should never be reached")
    }
}

/*
 * JsonCustomProperties are properties which can be added to the object, but are not
 * part of the specification. All subclasses of id.walt.oid4vc.data.JsonDataObject
 * might have additional properties.
 *
 * When the spec is generated the standard way, a property with the name
 * "customParameters" is added. To fix this, this generator module
 * removes the "customParameters" from the spec and sets the additional
 * properties
 */
private object FixJsonCustomParameters : SwaggerSchemaGenerationModule {

    override fun applies(typeData: TypeData): Boolean {
        return typeData.members.any { it.name.equals("customParameters") }
    }

    override fun generate(context: SwaggerSchemaGenerationModule.Context): Schema<*> {
        val typeData = context.typeData
        typeData.members.removeIf { it.name.equals("customParameters") }
        val generated = context.generate(typeData)
        generated.additionalProperties = Schema<Any>()
        generated.required?.removeIf { it.equals("customParameters") }
        return generated
    }
}


/**
 * This analyzer is needed for attributes like:
 * @Contextual val id: Uuid
 */
private object ContextualSerializationTypeAnalyzerModule : SerializationTypeAnalyzerModule {

    // at the moment only UUID is supported
    private val classToKindMap = mapOf("kotlin.uuid.Uuid" to PrimitiveKind.STRING)

    override fun applies(descriptor: SerialDescriptor): Boolean {
        return descriptor.kind == SerialKind.CONTEXTUAL
                && descriptor.serialName.startsWith("kotlinx.serialization.ContextualSerializer")
                && classToKindMap.keys.contains(referencedClassName(descriptor))
    }

    override fun analyze(context: SerializationTypeAnalyzerModule.Context): WrappedTypeData {
        val internalClassName = referencedClassName(context.descriptor)
        val result = context.analyze(object : SerialDescriptor {
            override val serialName: String
                get() = internalClassName
            override val kind: SerialKind
                get() = classToKindMap.get(internalClassName)!!
            override val elementsCount: Int
                get() = context.descriptor.elementsCount

            override fun getElementName(index: Int): String =
                context.descriptor.getElementName(index)

            override fun getElementIndex(name: String): Int =
                context.descriptor.getElementIndex(name)

            override fun getElementAnnotations(index: Int): List<Annotation> =
                context.descriptor.getElementAnnotations(index)

            override fun getElementDescriptor(index: Int): SerialDescriptor =
                context.descriptor.getElementDescriptor(index)

            override fun isElementOptional(index: Int): Boolean =
                context.descriptor.isElementOptional(index)
        })
        return result
    }

    private fun referencedClassName(descriptor: SerialDescriptor): String {
        val descriptorString = descriptor.toString()
        return "kClass:\\s+class\\s+([.a-z0-9]+)".toRegex(RegexOption.IGNORE_CASE)
            .find(descriptorString)!!.groups[1]!!.value
    }
}

object CustomTypeOverrides {
    class KotlinxInstant : SchemaOverwriteModule(
        identifier = Instant::class.qualifiedName!!,
        schema = {
            Schema<Any>().also {
                it.types = setOf("string")
                it.format = "date-time"
            }
        }
    )

    class JsonElement : SchemaOverwriteModule(
        identifier = kotlinx.serialization.json.JsonElement::class.qualifiedName!!,
        schema = {
            Schema<Any>().also {
                it.types = setOf("object")
            }
        },
    )

    class JsonObject :
        SchemaOverwriteModule(
            identifier = kotlinx.serialization.json.JsonObject::class.qualifiedName!!,
            schema = {
                Schema<Any>().also {
                    it.types = setOf("object")
                }
            },
        )

    class JsonArray : SchemaOverwriteModule(
        identifier = kotlinx.serialization.json.JsonArray::class.qualifiedName!!,
        schema = {
            Schema<Any>().also {
                it.types = setOf("array")
            }
        },
    )

    class SdMap : SchemaOverwriteModule(
        identifier = "id.walt.sdjwt.SDMap",
        schema = {
            Schema<Any>().also {
                it.types = setOf("object")
            }
        },
    )

    // TODO: real implementation which analyzes inheritance and generates schema
    class QuickFixPolymorphic : SchemaOverwriteModule(
        identifier = kotlinx.serialization.Polymorphic::class.qualifiedName!!,
        schema = {
            Schema<Any>().also {
                it.types = setOf("object")
            }
        },
    ) {
        override fun applies(descriptor: SerialDescriptor): Boolean {
            return descriptor.serialName.startsWith("kotlinx.serialization.Polymorphic")
        }

        override fun analyze(context: SerializationTypeAnalyzerModule.Context): WrappedTypeData {
            val original = super.analyze(context)
            return original.copy(
                typeData = original.typeData.copy(
                    identifyingName = TypeName("kotlinx.serialization.Polymorphic", "Polymorphic"),
                    descriptiveName = TypeName("kotlinx.serialization.Polymorphic", short = "Polymorphic"),
                ),
            )
        }

        override fun analyze(
            context: ReflectionTypeAnalyzerModule.Context,
            minimalTypeData: MinimalTypeData
        ): WrappedTypeData {
            TODO("Seems not to be needed")
        }
    }
}
