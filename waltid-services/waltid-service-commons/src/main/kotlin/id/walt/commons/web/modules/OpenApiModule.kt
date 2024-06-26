package id.walt.commons.web.modules

import id.walt.commons.config.statics.BuildConfig
import id.walt.commons.config.statics.ServiceConfig
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.KTypeDescriptor
import io.github.smiley4.ktorswaggerui.dsl.config.OpenApiInfo
import io.github.smiley4.ktorswaggerui.dsl.config.PluginConfigDsl
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.github.smiley4.schemakenerator.core.connectSubTypes
import io.github.smiley4.schemakenerator.core.handleNameAnnotation
import io.github.smiley4.schemakenerator.reflection.collectSubTypes
import io.github.smiley4.schemakenerator.reflection.processReflection
import io.github.smiley4.schemakenerator.serialization.processKotlinxSerialization
import io.github.smiley4.schemakenerator.swagger.compileReferencingRoot
import io.github.smiley4.schemakenerator.swagger.data.TitleType
import io.github.smiley4.schemakenerator.swagger.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.handleCoreAnnotations
import io.github.smiley4.schemakenerator.swagger.withAutoTitle
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.time.Duration.Companion.nanoseconds

object OpenApiModule {

    object OpenApiConfig {
        var customInfo: (OpenApiInfo.() -> Unit)? = null

        var custom: (PluginConfigDsl.() -> Unit)? = null
    }

    private fun KType.processWithKotlinxSerializationGenerator() = processKotlinxSerialization()
        .connectSubTypes()
        .handleNameAnnotation()
        .generateSwaggerSchema()
        .handleCoreAnnotations()
        .withAutoTitle(TitleType.SIMPLE)
        .compileReferencingRoot()

    private fun KType.processWithReflectionGenerator() =
        collectSubTypes()
            .processReflection()
            .connectSubTypes()
            .handleNameAnnotation()
            .generateSwaggerSchema()
            .handleCoreAnnotations()
            .withAutoTitle(TitleType.SIMPLE)
            .compileReferencingRoot()

    // Module
    fun Application.enable() {
        install(SwaggerUI) {

            examples {
                example("UUID") {
                    value = "12345678-abcd-9876-efgh-543210123456"
                }

                example("Instant") {
                    value = Clock.System.now().toString()
                }

                encoder { type, example ->
                    if (type is KTypeDescriptor) {
                        println("Example for: ${type.type}; example is: $example (${example!!::class.simpleName})")
                        Json.encodeToString(Json.serializersModule.serializer(type.type), example)
                    } else {
                        println("Example not; as type is: $type")
                        example
                    }
                }
            }

            schemas {
                val kotlinxPrefixes = listOf("id.walt")

                generator = { type ->

                    if (kotlinxPrefixes.any { type.toString().startsWith(it) }) {
                        runCatching {
//                             println("Trying kotlinx schema with: $type")
                            type.processWithKotlinxSerializationGenerator()
                        }.recover { ex ->
//                             println("Falling back to reflection schema with: $type, due to $ex")
                            type.processWithReflectionGenerator()
                        }.getOrElse { ex ->
                            error("Could neither parse with kotlinx nor reflection: $type, due to $ex")
                        }
                    } else type.processWithReflectionGenerator()
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
            swagger {
                showTagFilterInput = true
            }
        }

        routing {
            route("swagger") {
                swaggerUI("/api.json")
            }

            route("api.json") {
                openApiSpec()
            }

            get("/", {
                summary = "Redirect to swagger interface for API documentation"
            }) {
                context.respondRedirect("swagger")
            }
        }
    }
}

private fun Instant.roundToSecond(): Instant =
    minus(nanosecondsOfSecond.nanoseconds)
