package id.walt.commons.web.modules

import com.sksamuel.hoplite.simpleName
import id.walt.commons.config.statics.BuildConfig
import id.walt.commons.config.statics.ServiceConfig
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.ExampleEncoder
import io.github.smiley4.ktoropenapi.config.InfoConfig
import io.github.smiley4.ktoropenapi.config.OpenApiPluginConfig
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktoropenapi.config.descriptors.AnyOfTypeDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.ArrayTypeDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.EmptyTypeDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.KTypeDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.RefTypeDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.SerialTypeDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.SwaggerTypeDescriptor
import io.github.smiley4.ktoropenapi.config.descriptors.TypeDescriptor
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorredoc.redoc
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.github.smiley4.schemakenerator.core.data.InitialKTypeData
import io.github.smiley4.schemakenerator.core.data.InitialTypeData
import io.github.smiley4.schemakenerator.serialization.data.InitialSerialDescriptorTypeData
import io.klogging.noCoLogger
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.nanoseconds

object OpenApiModule {

    private val logger = noCoLogger("OpenAPI")

    object OpenApiConfig {
        var customInfo: (InfoConfig.() -> Unit)? = null

        var custom: (OpenApiPluginConfig.() -> Unit)? = null
    }

    // Module
    fun Application.enable() {
        install(OpenApi) {

            schemas {
                val kotlinxGenerator = SchemaGenerator.kotlinx()
                val reflectionGenerator = SchemaGenerator.reflection()

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

                fun TypeDescriptor.typeName(): String = when(this) {
                    is SwaggerTypeDescriptor -> "${schema.name} ${schema.type} (SwaggerType)"
                    is KTypeDescriptor -> type.simpleName + "<" + type.arguments.joinToString { it.type?.simpleName.toString() } + "> (KType)"
                    is SerialTypeDescriptor -> "${descriptor.serialName} (SerialType)"
                    is AnyOfTypeDescriptor -> "Any of ${this.types.map { it.typeName() } } (AnyOfType)"
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
                    Questions about anything here? Visit <a href='https://github.com/walt-id/#join-the-community'>support</a>.

                """.trimIndent().replace("\n", "<br/>")

                contact {
                    url = "https://walt.id"
                    name = "walt.id"
                    email = "office@walt.id"
                }
                license {
                    name = "Apache 2.0"
                    identifier = "Apache-2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.html"
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
                summary = "Redirect to swagger interface for API documentation"
            }) {
                call.respondRedirect("swagger")
            }
        }

//        install(SwaggerUI) {

        /*examples {
            example("Uuid") {
                value = "12345678-abcd-9876-efgh-543210123456"
            }

            example("Instant") {
                value = Clock.System.now().toString()
            }

            encoder { type, example ->
                if (type is KTypeDescriptor) {
                    encodeSwaggerExample(type, example)
                } else {
                    logger.trace { "No type descriptor for example, type is: $type" }
                    example
                }
            }
        }

        schemas {
            val kotlinxPrefixes = listOf("id.walt")

            generator = { type ->

                if (kotlinxPrefixes.any { type.toString().startsWith(it) }) {
                    runCatching {
                        // println("Trying kotlinx schema with: $type")
                        type.processWithKotlinxSerializationGenerator()
                    }.recover { ex ->
                        logger.trace { "Falling back to reflection schema with: $type, due to $ex" }
                        type.processWithReflectionGenerator()
                    }.getOrElse { ex ->
                        error("Could neither parse with kotlinx nor reflection: $type, due to $ex")
                    }
                } else type.processWithReflectionGenerator()
            }
        }*/


    }

    private val skippedTypes = listOf(typeOf<String>(), typeOf<Enum<*>>())

    private val exampleJson = Json {
        encodeDefaults = true
        explicitNulls = false
    }
}

private fun Instant.roundToSecond(): Instant =
    minus(nanosecondsOfSecond.nanoseconds)


